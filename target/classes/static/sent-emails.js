(function () {
  "use strict";

  var appEl = document.getElementById("sent-emails-app");
  var queueWrapEl = document.getElementById("sent-emails-queue-wrap");
  var logWrapEl = document.getElementById("sent-emails-log-wrap");
  var queueMetaEl = document.getElementById("sent-emails-queue-meta");
  var statusEl = document.getElementById("sent-emails-status");
  var logCountEl = document.getElementById("sent-emails-log-count");
  var refreshBtn = document.getElementById("sent-emails-refresh");
  var yearInput = document.getElementById("sent-emails-year");
  var yearApplyBtn = document.getElementById("sent-emails-year-apply");
  var employeeFilterEl = document.getElementById("sent-emails-employee-filter");

  var weekPrevBtn = document.getElementById("sent-emails-week-prev");
  var weekNextBtn = document.getElementById("sent-emails-week-next");
  var weekLabelEl = document.getElementById("sent-emails-week-label");
  var weekSubtitleEl = document.getElementById("sent-emails-week-subtitle");
  var navHomeEl = document.getElementById("sent-emails-nav-home");
  var navEmployeesEl = document.getElementById("sent-emails-nav-employees");
  var navProjectsEl = document.getElementById("sent-emails-nav-projects");
  var navNotificationsEl = document.getElementById("sent-emails-nav-notifications");
  var navForecastEl = document.getElementById("sent-emails-nav-forecast");
  var navGoalsEl = document.getElementById("sent-emails-nav-goals");

  var activeWeekKey = (appEl && appEl.getAttribute("data-initial-week")) || "";
  var currentWeekKey = "";
  var sendingByKey = {};

  function parseYearInt(value) {
    var n = parseInt(String(value), 10);
    if (!Number.isFinite(n) || n < 2000 || n > 2100) return null;
    return n;
  }

  var urlParamsBoot = new URLSearchParams(window.location.search);
  var yearFromUrl = parseYearInt(urlParamsBoot.get("year"));
  var yearFromEl = parseYearInt(appEl && appEl.getAttribute("data-initial-year"));
  var activeYear =
    yearFromUrl !== null ? yearFromUrl : yearFromEl !== null ? yearFromEl : new Date().getUTCFullYear();

  /** Applied once roster loads — select value synced from URL otherwise. */
  var pendingEmployeeFilterId = String(urlParamsBoot.get("employeeId") || "").trim();

  var lastQueuePayload = null;
  var lastLogList = [];

  function getSelectedEmployeeFilter() {
    if (!employeeFilterEl) return "";
    var v = String(employeeFilterEl.value || "").trim();
    return v;
  }

  function employeeLabelById(items, id) {
    var want = String(id || "");
    if (!want) return "";
    var list = Array.isArray(items) ? items : [];
    for (var i = 0; i < list.length; i += 1) {
      if (String(list[i].employeeId || "") === want && list[i].employeeName)
        return String(list[i].employeeName);
    }
    return "";
  }

  function employeeIdFromPlannedKey(pk) {
    var parts = String(pk || "").trim().split("|");
    if (parts[0] === "mgr" && parts.length === 4) return parts[2] || "";
    if (parts[0] === "emp" && parts.length === 5) return parts[2] || "";
    return "";
  }

  function filterQueueItems(allItems) {
    var sel = getSelectedEmployeeFilter();
    var items = Array.isArray(allItems) ? allItems : [];
    if (!sel) return items.slice();
    return items.filter(function (r) {
      return String(r.employeeId || "") === sel;
    });
  }

  function filterLogItems(allRows) {
    var sel = getSelectedEmployeeFilter();
    var rows = Array.isArray(allRows) ? allRows : [];
    if (!sel) return rows.slice();
    return rows.filter(function (row) {
      return employeeIdFromPlannedKey(row.plannedKey || "") === sel;
    });
  }

  function rebuildEmployeeFilterOptions(queueItems) {
    if (!employeeFilterEl) return;
    var keep = String(employeeFilterEl.value || "").trim();
    var uniq = {};
    var list = Array.isArray(queueItems) ? queueItems : [];
    list.forEach(function (row) {
      var id = String(row && row.employeeId != null ? row.employeeId : "").trim();
      if (!id) return;
      if (!uniq[id]) uniq[id] = row.employeeName || id;
    });
    var pairs = Object.keys(uniq).map(function (id) {
      return { id: id, name: uniq[id] || id };
    });
    pairs.sort(function (a, b) {
      return String(a.name).localeCompare(String(b.name));
    });

    employeeFilterEl.innerHTML = "";
    var optAll = document.createElement("option");
    optAll.value = "";
    optAll.textContent = "All employees";
    employeeFilterEl.appendChild(optAll);

    pairs.forEach(function (p) {
      var opt = document.createElement("option");
      opt.value = p.id;
      opt.textContent = p.name || p.id;
      employeeFilterEl.appendChild(opt);
    });

    var idSet = {};
    pairs.forEach(function (p) {
      idSet[p.id] = true;
    });

    if (pendingEmployeeFilterId && idSet[pendingEmployeeFilterId]) {
      employeeFilterEl.value = pendingEmployeeFilterId;
      pendingEmployeeFilterId = "";
    } else if (keep && idSet[keep]) {
      employeeFilterEl.value = keep;
    } else {
      employeeFilterEl.value = "";
    }

    syncUrlParams();
    refreshWeekUi();
  }

  /** Re-render cached queue + log when only the employee filter changed (no HTTP). */
  function repaintFilteredViews() {
    if (lastQueuePayload) renderQueue(lastQueuePayload, { skipRebuildOptions: true });
    renderLogTable(lastLogList);
  }

  function parseWeekKey(value) {
    var key = String(value || "").trim();
    if (!/^\d{4}-\d{2}-\d{2}$/.test(key)) return null;
    var d = new Date(key + "T00:00:00Z");
    if (isNaN(d.getTime())) return null;
    return toWeekKey(d);
  }

  function toWeekKey(dateObj) {
    var d = new Date(Date.UTC(dateObj.getUTCFullYear(), dateObj.getUTCMonth(), dateObj.getUTCDate()));
    var weekday = d.getUTCDay();
    var mondayShift = weekday === 0 ? -6 : 1 - weekday;
    d.setUTCDate(d.getUTCDate() + mondayShift);
    return d.toISOString().slice(0, 10);
  }

  function shiftWeekKey(weekKey, offsetWeeks) {
    var parsed = parseWeekKey(weekKey) || toWeekKey(new Date());
    var d = new Date(parsed + "T00:00:00Z");
    d.setUTCDate(d.getUTCDate() + offsetWeeks * 7);
    return toWeekKey(d);
  }

  function formatWeekLabel(weekKey) {
    var parsed = parseWeekKey(weekKey);
    if (!parsed) return "Week";
    var start = new Date(parsed + "T00:00:00Z");
    var end = new Date(start.getTime());
    end.setUTCDate(end.getUTCDate() + 6);
    var startLabel = start.toLocaleDateString(undefined, { month: "short", day: "numeric", year: "numeric" });
    var endLabel = end.toLocaleDateString(undefined, { month: "short", day: "numeric", year: "numeric" });
    return "Week: " + startLabel + " — " + endLabel;
  }

  function syncUrlParams() {
    var parsed = parseWeekKey(activeWeekKey);
    var url = new URL(window.location.href);
    if (parsed) url.searchParams.set("week", parsed);
    else url.searchParams.delete("week");
    url.searchParams.set("year", String(activeYear));
    var empSel = getSelectedEmployeeFilter();
    if (empSel) url.searchParams.set("employeeId", empSel);
    else url.searchParams.delete("employeeId");
    window.history.replaceState(null, "", url.toString());
  }

  function withWeekAndYear(path) {
    var url = new URL(path, window.location.origin);
    var wk = parseWeekKey(activeWeekKey);
    if (wk) url.searchParams.set("week", wk);
    url.searchParams.set("year", String(activeYear));
    var empSel = getSelectedEmployeeFilter();
    if (empSel) url.searchParams.set("employeeId", empSel);
    return url.pathname + url.search;
  }

  function refreshWeekUi() {
    if (weekLabelEl) weekLabelEl.textContent = formatWeekLabel(activeWeekKey);
    if (weekSubtitleEl) {
      weekSubtitleEl.textContent =
        "Roster for " +
        formatWeekLabel(activeWeekKey).replace(/^Week: /, "") +
        " · " +
        (activeWeekKey === currentWeekKey ? "Matches current planning week." : "Not the live planning week.");
    }
    if (navHomeEl) navHomeEl.href = withWeekAndYear("/");
    if (navEmployeesEl) navEmployeesEl.href = withWeekAndYear("/admin/employees");
    if (navProjectsEl) navProjectsEl.href = withWeekAndYear("/admin/projects");
    if (navNotificationsEl) navNotificationsEl.href = withWeekAndYear("/admin/notifications");
    if (navForecastEl) navForecastEl.href = withWeekAndYear("/forecast");
    if (navGoalsEl) navGoalsEl.href = withWeekAndYear("/goals");
  }

  function setStatus(message, isError) {
    if (!statusEl) return;
    statusEl.textContent = message || "";
    statusEl.className = isError ? "text-danger" : "subtle";
  }

  function escapeHtml(value) {
    var div = document.createElement("div");
    div.textContent = value == null ? "" : String(value);
    return div.innerHTML;
  }

  function cssEscapeAttr(value) {
    var v = String(value || "");
    if (typeof CSS !== "undefined" && typeof CSS.escape === "function") return CSS.escape(v);
    return v.replace(/\\/g, "\\\\").replace(/"/g, '\\"');
  }

  function formatWhen(iso) {
    var s = String(iso || "").trim();
    if (!s) return "—";
    var d = new Date(s);
    if (isNaN(d.getTime())) return escapeHtml(s);
    return escapeHtml(d.toLocaleString());
  }

  function formatQueueMessagePurpose(row) {
    var v = String((row && row.messageVariant) || "");
    if (v === "january_goal_setting") return escapeHtml("Annual goals setup");
    return escapeHtml("Check-in");
  }

  function formatPlannedSendDate(iso) {
    var s = String(iso || "").trim();
    if (!/^\d{4}-\d{2}-\d{2}$/.test(s)) return "—";
    var p = s.split("-");
    var d = new Date(parseInt(p[0], 10), parseInt(p[1], 10) - 1, parseInt(p[2], 10));
    if (isNaN(d.getTime())) return escapeHtml(s);
    return escapeHtml(
      d.toLocaleDateString(undefined, {
        weekday: "short",
        year: "numeric",
        month: "short",
        day: "numeric"
      })
    );
  }

  function renderQueue(data, opts) {
    var skipRebuildOptions = !!(opts && opts.skipRebuildOptions);
    if (!queueWrapEl) return;
    lastQueuePayload = data && typeof data === "object" ? data : {};
    var fullItems = Array.isArray(lastQueuePayload.items) ? lastQueuePayload.items : [];
    if (!skipRebuildOptions) rebuildEmployeeFilterOptions(fullItems);

    var items = filterQueueItems(fullItems);
    var filterId = getSelectedEmployeeFilter();

    if (queueMetaEl) {
      var base =
        "Goals year " +
        String(lastQueuePayload.year != null ? lastQueuePayload.year : "") +
        " · Roster Monday " +
        String(lastQueuePayload.weekKey != null ? lastQueuePayload.weekKey : "") +
        " · Notifications " +
        (lastQueuePayload.notificationsEnabled ? "enabled" : "disabled") +
        " (automated sends still require a scheduler).";
      if (filterId && fullItems.length) {
        var label = employeeLabelById(fullItems, filterId) || filterId;
        base += " Showing " + items.length + " of " + fullItems.length + " rows for " + label + ".";
      }
      base +=
        " Planned send dates are suggested targets (January sends use annual goals-setup copy; February–December use check-ins). Sending is manual.";
      queueMetaEl.textContent = base;
    }

    if (!fullItems.length) {
      queueWrapEl.innerHTML =
        '<p class="subtle">No employees on this week roster. Change the week above or open the allocation plan.</p>';
      return;
    }

    if (!items.length && filterId) {
      queueWrapEl.innerHTML =
        '<p class="subtle">No planned rows for this employee on this roster — pick another employee or adjust the plan.</p>';
      return;
    }

    queueWrapEl.innerHTML =
      '<table class="data-table"><thead><tr>' +
      "<th>Employee</th>" +
      "<th>Year</th>" +
      "<th>Period</th>" +
      "<th>Focus</th>" +
      "<th>Planned send</th>" +
      "<th>Recipient</th>" +
      "<th>To</th>" +
      "<th>Subject</th>" +
      "<th>Status</th>" +
      "<th>Sent</th>" +
      "<th>Error</th>" +
      "<th></th>" +
      "</tr></thead><tbody>" +
      items
        .map(function (row) {
          var recipientLabel =
            row.recipientRole === "manager"
              ? "Manager (" + escapeHtml(row.recipientName || "") + ")"
              : "Employee · reminder " + escapeHtml(row.reminderNumber != null ? String(row.reminderNumber) : "");
          var recipientMissing = !!row.recipientMissing;
          var st = row.lastStatus || "pending";
          var stHtml =
            st === "failed"
              ? '<span class="text-danger">failed</span>'
              : st === "sent"
                ? "sent"
                : '<span class="subtle">pending</span>';
          var err = recipientMissing ? "No login email" : escapeHtml(row.lastError || "");
          var busy = !!(row.plannedKey && sendingByKey[row.plannedKey]);
          var pkAttr = escapeHtml(row.plannedKey || "");
          var trClass =
            "sent-email-row sent-email-row--queue sent-email-row--" +
            (st === "sent" ? "sent" : st === "failed" ? "failed" : "pending");
          return (
            '<tr class="' +
            escapeHtml(trClass) +
            '">' +
            "<td>" +
            escapeHtml(row.employeeName || "") +
            "</td>" +
            "<td>" +
            escapeHtml(String(row.year != null ? row.year : "")) +
            "</td>" +
            "<td>" +
            escapeHtml(row.periodLabel || "") +
            "</td>" +
            "<td>" +
            formatQueueMessagePurpose(row) +
            "</td>" +
            "<td>" +
            formatPlannedSendDate(row.plannedSendDate || "") +
            "</td>" +
            "<td>" +
            recipientLabel +
            "</td>" +
            "<td>" +
            escapeHtml(row.recipientEmail || "") +
            "</td>" +
            "<td>" +
            escapeHtml(row.subject || "") +
            "</td>" +
            "<td>" +
            stHtml +
            "</td>" +
            "<td>" +
            formatWhen(row.lastSentAt || "") +
            "</td>" +
            '<td class="subtle">' +
            err +
            "</td>" +
            '<td style="white-space:nowrap">' +
            '<button type="button" class="btn btn-primary sent-email-send" data-planned-key="' +
            pkAttr +
            '"' +
            (recipientMissing ? " disabled" : "") +
            (busy ? " disabled" : "") +
            ">" +
            (busy ? "Sending…" : "Send") +
            "</button>" +
            "</td>" +
            "</tr>"
          );
        })
        .join("") +
      "</tbody></table>";
  }

  function renderLogTable(items) {
    if (!logWrapEl) return;
    lastLogList = Array.isArray(items) ? items : [];
    var fullList = lastLogList.slice();
    var list = filterLogItems(fullList);
    var sel = getSelectedEmployeeFilter();
    if (logCountEl) {
      var fullParts = sel
        ? "Showing " + list.length + " of " + fullList.length + " entries matching <code>" + escapeHtml(sel) + "</code> via planned keys."
        : fullList.length + " newest entries shown.";
      if (sel && list.length < fullList.length) {
        fullParts +=
          ' Rows without a <code>plannedKey</code> (e.g. notification tests) are hidden while filtering.';
      }
      logCountEl.innerHTML = fullParts;
    }
    if (!fullList.length) {
      logWrapEl.innerHTML = '<p class="subtle">Nothing logged yet.</p>';
      return;
    }
    if (!list.length) {
      logWrapEl.innerHTML =
        '<p class="subtle">No log rows for this employee (only planned sends embed the employee id in the key).</p>';
      return;
    }
    logWrapEl.innerHTML =
      '<table class="data-table"><thead><tr>' +
      "<th>Sent</th>" +
      "<th>To</th>" +
      "<th>Subject</th>" +
      "<th>Kind</th>" +
      "<th>Trigger</th>" +
      "<th>Planned key</th>" +
      "<th>Status</th>" +
      "<th>Error</th>" +
      "</tr></thead><tbody>" +
      list
        .map(function (row) {
          var logSt = row.status === "failed" ? "failed" : "sent";
          var logTrClass =
            "sent-email-row sent-email-row--log sent-email-row--" +
            logSt;
          return (
            '<tr class="' +
            escapeHtml(logTrClass) +
            '">' +
            "<td>" +
            formatWhen(row.sentAt) +
            "</td>" +
            "<td>" +
            escapeHtml(row.to) +
            "</td>" +
            "<td>" +
            escapeHtml(row.subject) +
            "</td>" +
            "<td><code>" +
            escapeHtml(row.kind || "") +
            "</code></td>" +
            "<td><code>" +
            escapeHtml(row.trigger || "") +
            "</code></td>" +
            "<td class=\"subtle\"><code>" +
            escapeHtml(row.plannedKey || "") +
            "</code></td>" +
            "<td>" +
            (row.status === "failed"
              ? '<span class="text-danger">failed</span>'
              : "sent") +
            "</td>" +
            "<td class=\"subtle\">" +
            escapeHtml(row.error || "") +
            "</td>" +
            "</tr>"
          );
        })
        .join("") +
      "</tbody></table>";
  }

  function loadQueue() {
    setStatus("Loading queue…");
    return fetch("/api/admin/notification-queue?year=" + encodeURIComponent(String(activeYear)) + "&week=" + encodeURIComponent(activeWeekKey || ""))
      .then(function (res) {
        if (!res.ok) return Promise.reject(new Error("Could not load notification queue."));
        return res.json();
      })
      .then(function (data) {
        setStatus("");
        renderQueue(data || {});
      })
      .catch(function (err) {
        setStatus((err && err.message) || "Failed to load queue.", true);
        renderQueue({ year: activeYear, weekKey: activeWeekKey, items: [], notificationsEnabled: false });
      });
  }

  function loadLog() {
    return fetch("/api/admin/sent-emails?limit=250")
      .then(function (res) {
        if (!res.ok) return Promise.reject(new Error("Could not load send log."));
        return res.json();
      })
      .then(function (data) {
        renderLogTable(data && data.items ? data.items : []);
      })
      .catch(function () {
        renderLogTable([]);
      });
  }

  function refreshAll() {
    return Promise.all([loadQueue(), loadLog()]);
  }

  function readBodyError(res) {
    return res.json().catch(function () {
      return {};
    }).then(function (body) {
      throw new Error((body && body.error) || "Send failed.");
    });
  }

  function markSendingUi(plannedKey, isSending) {
    if (!queueWrapEl || !plannedKey || !isSending) return;
    var selector = 'button.sent-email-send[data-planned-key="' + cssEscapeAttr(plannedKey) + '"]';
    var btn = queueWrapEl.querySelector(selector);
    if (!btn) return;
    btn.disabled = true;
    btn.textContent = "Sending…";
  }

  function sendRow(plannedKey) {
    if (!plannedKey || sendingByKey[plannedKey]) return;
    sendingByKey[plannedKey] = true;
    setStatus("Sending…");
    markSendingUi(plannedKey, true);

    return fetch("/api/admin/notification-queue/send", {
      method: "POST",
      headers: { "Content-Type": "application/json" },
      body: JSON.stringify({ plannedKey: plannedKey, week: activeWeekKey || "" })
    })
      .then(function (res) {
        if (!res.ok) return readBodyError(res);
        return res.json();
      })
      .then(function () {
        setStatus("Email sent.");
      })
      .catch(function (err) {
        setStatus((err && err.message) || "Send failed.", true);
      })
      .then(function () {
        delete sendingByKey[plannedKey];
        return refreshAll();
      });
  }

  if (queueWrapEl) {
    queueWrapEl.addEventListener("click", function (ev) {
      var btn = ev.target.closest("button.sent-email-send");
      if (!btn || !queueWrapEl.contains(btn) || btn.disabled) return;
      var pk = btn.getAttribute("data-planned-key");
      sendRow(pk);
    });
  }

  if (employeeFilterEl) {
    employeeFilterEl.addEventListener("change", function () {
      syncUrlParams();
      refreshWeekUi();
      repaintFilteredViews();
    });
  }

  if (refreshBtn) refreshBtn.addEventListener("click", refreshAll);

  if (yearApplyBtn && yearInput) {
    yearApplyBtn.addEventListener("click", function () {
      var parsed = parseYearInt(yearInput.value);
      activeYear = parsed !== null ? parsed : activeYear;
      yearInput.value = String(activeYear);
      syncUrlParams();
      refreshWeekUi();
      refreshAll();
    });
  }

  if (weekPrevBtn) {
    weekPrevBtn.addEventListener("click", function () {
      activeWeekKey = shiftWeekKey(activeWeekKey, -1);
      syncUrlParams();
      refreshWeekUi();
      refreshAll();
    });
  }
  if (weekNextBtn) {
    weekNextBtn.addEventListener("click", function () {
      activeWeekKey = shiftWeekKey(activeWeekKey, 1);
      syncUrlParams();
      refreshWeekUi();
      refreshAll();
    });
  }

  if (!parseWeekKey(activeWeekKey)) activeWeekKey = toWeekKey(new Date());
  currentWeekKey = toWeekKey(new Date());
  if (yearInput) yearInput.value = String(activeYear);
  syncUrlParams();
  refreshWeekUi();
  refreshAll();
})();
