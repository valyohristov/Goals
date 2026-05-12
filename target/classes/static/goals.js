(function () {
  "use strict";

  var appEl = document.getElementById("goals-app");
  var yearEl = document.getElementById("goals-year");
  var employeeWrap = document.getElementById("goals-employee-wrap");
  var employeeEl = document.getElementById("goals-employee");
  var refreshBtn = document.getElementById("goals-refresh");
  var saveBtn = document.getElementById("goals-save");
  var statusEl = document.getElementById("goals-status");
  var contentEl = document.getElementById("goals-content");
  var navHome = document.getElementById("goals-nav-home");
  var navEmployees = document.getElementById("goals-nav-employees");
  var navProjects = document.getElementById("goals-nav-projects");
  var navNotifications = document.getElementById("goals-nav-notifications");
  var navSentEmails = document.getElementById("goals-nav-sent-emails");
  var navForecast = document.getElementById("goals-nav-forecast");

  var checkinBackdrop = document.getElementById("goals-checkin-backdrop");
  var checkinModal = document.getElementById("goals-checkin-modal");
  var checkinModalTitle = document.getElementById("goals-checkin-modal-title");
  var checkinModalDate = document.getElementById("goals-checkin-modal-date");
  var checkinModalEmployee = document.getElementById("goals-checkin-modal-employee");
  var checkinModalManager = document.getElementById("goals-checkin-modal-manager");
  var checkinModalSave = document.getElementById("goals-checkin-modal-save");
  var checkinModalDelete = document.getElementById("goals-checkin-modal-delete");
  var checkinModalCancel = document.getElementById("goals-checkin-modal-cancel");
  var checkinModalDismiss = document.getElementById("goals-checkin-modal-dismiss");

  var MONTHS = [
    "January", "February", "March", "April", "May", "June",
    "July", "August", "September", "October", "November", "December"
  ];

  var CHECK_IN_PERIODS = [
    "January–February · annual goals set in January",
    "March–April",
    "May–June",
    "July–August",
    "September–October",
    "November–December"
  ];

  var activeWeekKey = (appEl && appEl.getAttribute("data-initial-week")) || "";
  var selectedYear = parseInt((appEl && appEl.getAttribute("data-initial-year")) || "0", 10) || new Date().getUTCFullYear();
  var latestPayload = null;
  var navTier = (appEl && appEl.getAttribute("data-nav-tier")) || "employee";
  var showEmployeePicker = navTier === "admin" || navTier === "team";
  var dialogGoalIndex = -1;
  var dialogCheckinIndex = -1;

  if (employeeWrap && !showEmployeePicker) {
    employeeWrap.hidden = true;
  }

  function parseWeekKey(value) {
    var key = String(value || "").trim();
    if (!/^\d{4}-\d{2}-\d{2}$/.test(key)) return null;
    var d = new Date(key + "T00:00:00Z");
    if (isNaN(d.getTime())) return null;
    var weekday = d.getUTCDay();
    var mondayShift = weekday === 0 ? -6 : 1 - weekday;
    d.setUTCDate(d.getUTCDate() + mondayShift);
    return d.toISOString().slice(0, 10);
  }

  function toWeekKey(dateObj) {
    var d = new Date(Date.UTC(dateObj.getUTCFullYear(), dateObj.getUTCMonth(), dateObj.getUTCDate()));
    var weekday = d.getUTCDay();
    var mondayShift = weekday === 0 ? -6 : 1 - weekday;
    d.setUTCDate(d.getUTCDate() + mondayShift);
    return d.toISOString().slice(0, 10);
  }

  function withWeek(path) {
    var parsed = parseWeekKey(activeWeekKey);
    var url = new URL(path, window.location.origin);
    if (parsed) url.searchParams.set("week", parsed);
    return url.pathname + url.search;
  }

  function setYearInUrl(y) {
    var url = new URL(window.location.href);
    url.searchParams.set("year", String(y));
    var w = parseWeekKey(activeWeekKey);
    if (w) url.searchParams.set("week", w);
    window.history.replaceState(null, "", url.toString());
  }

  function escapeHtml(value) {
    var div = document.createElement("div");
    div.textContent = value == null ? "" : String(value);
    return div.innerHTML;
  }

  function setStatus(msg, isError) {
    if (!statusEl) return;
    statusEl.textContent = msg || "";
    statusEl.className = isError ? "text-danger" : "subtle";
  }

  function fillYearSelect() {
    if (!yearEl) return;
    var cy = new Date().getUTCFullYear();
    yearEl.innerHTML = "";
    for (var y = cy - 1; y <= cy + 2; y += 1) {
      yearEl.insertAdjacentHTML("beforeend", "<option value=\"" + y + "\"" + (y === selectedYear ? " selected" : "") + ">" + y + "</option>");
    }
  }

  function normalizeCheckIns(list) {
    var out = [];
    for (var i = 0; i < 6; i += 1) {
      var ci = list && list[i] && typeof list[i] === "object" ? list[i] : {};
      out.push({
        meetingDate: String(ci.meetingDate || ""),
        employeeNotes: String(ci.employeeNotes || ""),
        managerNotes: String(ci.managerNotes || "")
      });
    }
    return out;
  }

  function isCheckInReviewed(ci) {
    if (!ci) return false;
    if (String(ci.meetingDate || "").trim()) return true;
    if (String(ci.employeeNotes || "").trim()) return true;
    if (String(ci.managerNotes || "").trim()) return true;
    return false;
  }

  /** Check-in button color: none / employee comment / manager comment (manager wins if both). */
  function checkinCommentButtonClass(ci) {
    if (!ci) return "goals-row-checkin-btn--cc-none";
    if (String(ci.managerNotes || "").trim()) return "goals-row-checkin-btn--cc-manager";
    if (String(ci.employeeNotes || "").trim()) return "goals-row-checkin-btn--cc-employee";
    return "goals-row-checkin-btn--cc-none";
  }

  function checkinCommentTitle(ci, periodLabel, goalIdx) {
    var gn = (goalIdx | 0) + 1;
    var label = "Goal " + gn + " — Check-in: " + periodLabel;
    if (!ci) return label + " — no comments yet";
    if (String(ci.managerNotes || "").trim()) return label + " — manager commented";
    if (String(ci.employeeNotes || "").trim()) return label + " — employee commented";
    return label + " — no comments yet";
  }

  function readGoalsFromDom() {
    var goals = [];
    for (var g = 0; g < 2; g += 1) {
      var titleEl = document.getElementById("goal-title-" + g);
      var descEl = document.getElementById("goal-desc-" + g);
      var gid = document.getElementById("goal-id-" + g);
      var months = [];
      for (var m = 0; m < 12; m += 1) {
        var doing = document.getElementById("g" + g + "-m" + m + "-doing");
        var outcome = document.getElementById("g" + g + "-m" + m + "-outcome");
        months.push({
          doing: doing ? doing.value : "",
          outcome: outcome ? outcome.value : ""
        });
      }
      var row = {
        id: gid ? gid.value : "",
        title: titleEl ? titleEl.value.trim() : "",
        description: descEl ? descEl.value : "",
        months: months
      };
      if (latestPayload && latestPayload.goals && latestPayload.goals[g]) {
        row.checkIns = normalizeCheckIns(latestPayload.goals[g].checkIns);
      }
      goals.push(row);
    }
    return goals;
  }

  function buildPutBody() {
    var body = {
      year: selectedYear,
      employeeId: latestPayload.employeeId
    };
    if (latestPayload.canEdit) {
      body.goals = readGoalsFromDom();
      return body;
    }
    body.goals = [0, 1].map(function (gi) {
      var g = latestPayload.goals[gi] || {};
      return { id: g.id || "", checkIns: normalizeCheckIns(g.checkIns) };
    });
    return body;
  }

  function putGoals(body, doneMsg) {
    return fetch(withWeek("/api/goals"), {
      method: "PUT",
      headers: { "Content-Type": "application/json" },
      body: JSON.stringify(body)
    })
      .then(function (res) {
        if (!res.ok) return res.json().then(function (b) { throw new Error(b && b.error ? b.error : "Save failed"); });
        return res.json();
      })
      .then(function (data) {
        latestPayload.goals = data.goals;
        render(latestPayload);
        setStatus(doneMsg || "Saved.");
      });
  }

  function closeCheckinDialog() {
    dialogGoalIndex = -1;
    dialogCheckinIndex = -1;
    if (checkinModal) checkinModal.hidden = true;
    if (checkinBackdrop) checkinBackdrop.hidden = true;
    document.body.classList.remove("goals-modal-open");
  }

  function openCheckinDialog(goalIdx, periodIdx) {
    if (!latestPayload || periodIdx < 0 || periodIdx > 5 || goalIdx < 0 || goalIdx > 1) return;
    var gPayload = latestPayload.goals[goalIdx];
    if (!gPayload) return;
    dialogGoalIndex = goalIdx;
    dialogCheckinIndex = periodIdx;
    var ciList = normalizeCheckIns(gPayload.checkIns);
    var ci = ciList[periodIdx] || { meetingDate: "", employeeNotes: "", managerNotes: "" };
    var canEmp = !!latestPayload.canEditEmployeeCheckIns;
    var canMgr = !!latestPayload.canEditManagerCheckIns;
    var canDate = canEmp || canMgr;
    var canSaveDialog = canEmp || canMgr;

    if (checkinModalTitle) {
      checkinModalTitle.textContent = "Goal " + (goalIdx + 1) + " — Check-in: " + CHECK_IN_PERIODS[periodIdx];
    }
    if (checkinModalDate) {
      checkinModalDate.value = ci.meetingDate || "";
      checkinModalDate.disabled = !canDate;
    }
    if (checkinModalEmployee) {
      checkinModalEmployee.value = ci.employeeNotes || "";
      checkinModalEmployee.readOnly = !canEmp && !canMgr;
      checkinModalEmployee.placeholder = canEmp
        ? "Prep or reflections (employee)"
        : canMgr
        ? "Employee notes (you can edit)"
        : "(Employee notes — read only)";
    }
    if (checkinModalManager) {
      checkinModalManager.value = ci.managerNotes || "";
      checkinModalManager.readOnly = !canMgr;
      checkinModalManager.placeholder = canMgr ? "Discussion summary, agreements, next steps (manager)" : "(Manager notes — read only)";
    }
    if (checkinModalSave) {
      checkinModalSave.hidden = !canSaveDialog;
    }
    if (checkinModalDelete) {
      checkinModalDelete.hidden = !(latestPayload.canDeleteCheckIns && isCheckInReviewed(ci));
    }

    if (checkinBackdrop) checkinBackdrop.hidden = false;
    if (checkinModal) checkinModal.hidden = false;
    document.body.classList.add("goals-modal-open");

    if (canDate && checkinModalDate && !checkinModalDate.disabled) {
      checkinModalDate.focus();
    } else if (canEmp && checkinModalEmployee) {
      checkinModalEmployee.focus();
    } else if (canMgr && checkinModalManager) {
      checkinModalManager.focus();
    }
  }

  function deleteCheckinDialog() {
    if (!latestPayload || !latestPayload.canDeleteCheckIns) return;
    if (dialogGoalIndex < 0 || dialogCheckinIndex < 0) return;
    if (
      !window.confirm(
        "Remove this check-in (meeting date and all notes)? This cannot be undone."
      )
    ) {
      return;
    }
    setStatus("Deleting check-in…");
    fetch(withWeek("/api/goals/check-in"), {
      method: "DELETE",
      headers: { "Content-Type": "application/json" },
      body: JSON.stringify({
        year: selectedYear,
        employeeId: latestPayload.employeeId,
        goalIndex: dialogGoalIndex,
        periodIndex: dialogCheckinIndex
      })
    })
      .then(function (res) {
        if (!res.ok) {
          return res.json().then(function (b) {
            throw new Error(b && b.error ? b.error : "Delete failed");
          });
        }
        return res.json();
      })
      .then(function (data) {
        latestPayload.goals = data.goals;
        render(latestPayload);
        closeCheckinDialog();
        setStatus("Check-in removed.");
      })
      .catch(function (err) {
        setStatus(err.message || "Delete failed.", true);
      });
  }

  function saveCheckinDialog() {
    if (dialogGoalIndex < 0 || dialogCheckinIndex < 0 || !latestPayload) return;
    var gIdx = dialogGoalIndex;
    var idx = dialogCheckinIndex;
    var canEmp = !!latestPayload.canEditEmployeeCheckIns;
    var canMgr = !!latestPayload.canEditManagerCheckIns;
    var canDate = canEmp || canMgr;
    if (!canEmp && !canMgr) return;

    var gPl = latestPayload.goals[gIdx];
    if (!gPl) return;
    var list = normalizeCheckIns(gPl.checkIns);
    var prev = list[idx];
    var next = {
      meetingDate: canDate && checkinModalDate ? checkinModalDate.value : prev.meetingDate,
      employeeNotes:
        (canEmp || canMgr) && checkinModalEmployee ? checkinModalEmployee.value : prev.employeeNotes,
      managerNotes: canMgr && checkinModalManager ? checkinModalManager.value : prev.managerNotes
    };
    var merged = list.slice();
    merged[idx] = next;
    latestPayload.goals[gIdx].checkIns = merged;

    setStatus("Saving check-in…");
    putGoals(buildPutBody(), "Check-in saved.")
      .then(function () {
        closeCheckinDialog();
      })
      .catch(function (err) {
        setStatus(err.message || "Save failed.", true);
      });
  }

  function render(payload) {
    latestPayload = payload;
    if (latestPayload.goals && latestPayload.goals.length) {
      latestPayload.goals = latestPayload.goals.map(function (gg) {
        var o = gg && typeof gg === "object" ? gg : {};
        o.checkIns = normalizeCheckIns(o.checkIns);
        return o;
      });
    }
    if (!contentEl) return;
    var name = escapeHtml(payload.employeeName || "");
    var canEdit = !!payload.canEdit;
    var canEmpCi = !!payload.canEditEmployeeCheckIns;
    var canMgrCi = !!payload.canEditManagerCheckIns;
    var canSave = canEdit || canEmpCi || canMgrCi;
    if (saveBtn) saveBtn.hidden = !canSave;

    var head = "<header class=\"goals-employee-banner\"><h2 class=\"goals-employee-name\">" + name + "</h2>" +
      "<p class=\"subtle\">Year " + escapeHtml(String(payload.year)) +
      (canSave ? "" : " · read-only") + "</p></header>";

    var blocks = "";
    var goalList = latestPayload.goals || [];
    for (var g = 0; g < 2; g += 1) {
      var goal = goalList[g] || { id: "", title: "", description: "", months: [], checkIns: [] };
      var ciList = normalizeCheckIns(goal.checkIns);
      var reviewedByMonth = [];
      for (var ri = 0; ri < 12; ri += 1) {
        reviewedByMonth[ri] = isCheckInReviewed(ciList[Math.floor(ri / 2)]);
      }
      var titleInput = canEdit
        ? "<input type=\"text\" class=\"goal-title-input\" id=\"goal-title-" + g + "\" placeholder=\"Goal " + (g + 1) + " title (optional)\" value=\"" + escapeHtml(goal.title || "") + "\" />"
        : "<p class=\"goal-title-readonly\"><strong>" + escapeHtml(goal.title || ("Goal " + (g + 1))) + "</strong></p>";
      var descVal = escapeHtml(goal.description || "");
      var descField = canEdit
        ? "<label class=\"goal-desc-label\"><span class=\"subtle\">Description</span><textarea id=\"goal-desc-" + g + "\" class=\"goal-desc-input\" rows=\"3\" placeholder=\"What this goal is about, success criteria, links…\">" + descVal + "</textarea></label>"
        : "<div class=\"goal-desc-read\"><span class=\"subtle\">Description</span><div class=\"goals-cell-read\">" + (descVal ? descVal.replace(/\n/g, "<br/>") : "—") + "</div></div>";
      var hiddenId = "<input type=\"hidden\" id=\"goal-id-" + g + "\" value=\"" + escapeHtml(goal.id || "") + "\" />";
      var rows = "";
      for (var m = 0; m < 12; m += 1) {
        var rowClass = reviewedByMonth[m] ? " class=\"goals-month-row goals-month-row--reviewed\"" : " class=\"goals-month-row\"";
        var mon = goal.months && goal.months[m] ? goal.months[m] : { doing: "", outcome: "" };
        var doingVal = escapeHtml(mon.doing || "");
        var outVal = escapeHtml(mon.outcome || "");
        var doingCell = canEdit
          ? "<textarea id=\"g" + g + "-m" + m + "-doing\" class=\"goals-cell-input\" rows=\"2\" placeholder=\"What I will do\">" + doingVal + "</textarea>"
          : "<div class=\"goals-cell-read\">" + (doingVal ? doingVal.replace(/\n/g, "<br/>") : "—") + "</div>";
        var outCell = canEdit
          ? "<textarea id=\"g" + g + "-m" + m + "-outcome\" class=\"goals-cell-input\" rows=\"2\" placeholder=\"Expected outcome\">" + outVal + "</textarea>"
          : "<div class=\"goals-cell-read\">" + (outVal ? outVal.replace(/\n/g, "<br/>") : "—") + "</div>";
        var thInner = "<span class=\"goals-month-name\">" + escapeHtml(MONTHS[m]) + "</span>";
        if (m % 2 === 1) {
          var cidx = Math.floor(m / 2);
          var cic = ciList[cidx];
          var ccCls = checkinCommentButtonClass(cic);
          var tip = checkinCommentTitle(cic, CHECK_IN_PERIODS[cidx], g);
          thInner += "<button type=\"button\" class=\"btn goals-row-checkin-btn " + ccCls + "\" data-goal-idx=\"" + g + "\" data-checkin-idx=\"" + cidx + "\" title=\"" + escapeHtml(tip) + "\">Check-in</button>";
        }
        rows += "<tr" + rowClass + "><th scope=\"row\" class=\"goals-month-th\"><div class=\"goals-month-th-inner\">" + thInner + "</div></th><td>" + doingCell + "</td><td>" + outCell + "</td></tr>";
      }
      blocks += "<section class=\"panel goals-goal-panel\"><h3>Goal " + (g + 1) + "</h3>" + hiddenId + titleInput + descField +
        "<div class=\"goals-table-wrap\"><table class=\"data-table goals-year-table\"><thead><tr><th>Month</th><th>What I will do</th><th>Expected outcome</th></tr></thead><tbody>" +
        rows + "</tbody></table></div></section>";
    }

    contentEl.innerHTML = head + blocks;
  }

  function fetchGoals() {
    setStatus("Loading…");
    closeCheckinDialog();
    var url = new URL(withWeek("/api/goals"), window.location.origin);
    url.searchParams.set("year", String(selectedYear));
    if (employeeEl && !employeeWrap.hidden && employeeEl.value) {
      url.searchParams.set("employeeId", employeeEl.value);
    }
    fetch(url)
      .then(function (res) {
        if (!res.ok) return res.json().then(function (b) { throw new Error(b && b.error ? b.error : "Failed to load goals"); });
        return res.json();
      })
      .then(function (data) {
        activeWeekKey = parseWeekKey(data.weekKey) || activeWeekKey;
        selectedYear = parseInt(data.year, 10) || selectedYear;
        if (yearEl) yearEl.value = String(selectedYear);
        setYearInUrl(selectedYear);

        if (showEmployeePicker && employeeWrap && employeeEl) {
          if (data.employeePicker && data.employeePicker.length) {
            employeeWrap.hidden = false;
            employeeEl.innerHTML = data.employeePicker.map(function (e) {
              return "<option value=\"" + escapeHtml(e.id) + "\">" + escapeHtml(e.name) + "</option>";
            }).join("");
            employeeEl.value = data.employeeId;
          } else if (data.rosterEmpty) {
            employeeWrap.hidden = false;
            employeeEl.innerHTML = "<option value=\"\">(no employees in database)</option>";
          } else {
            employeeWrap.hidden = true;
          }
        } else if (employeeWrap) {
          employeeWrap.hidden = true;
        }

        render(data);
        if (data.rosterEmpty && data.hint) {
          setStatus(data.hint, true);
        } else {
          setStatus("");
        }
      })
      .catch(function (err) {
        setStatus(err.message || "Failed to load goals.", true);
        if (contentEl) contentEl.innerHTML = "";
      });
  }

  function saveGoals() {
    if (!latestPayload) return;
    var canSave =
      latestPayload.canEdit ||
      latestPayload.canEditEmployeeCheckIns ||
      latestPayload.canEditManagerCheckIns;
    if (!canSave) return;
    setStatus("Saving…");
    putGoals(buildPutBody(), "Saved.").catch(function (err) {
      setStatus(err.message || "Save failed.", true);
    });
  }

  function setupNav() {
    if (navHome) navHome.href = withWeek("/");
    if (navEmployees) {
      navEmployees.href = withWeek("/admin/employees");
      navEmployees.hidden = navTier !== "admin" && navTier !== "team";
    }
    if (navProjects) {
      navProjects.href = withWeek("/admin/projects");
      navProjects.hidden = navTier !== "admin";
    }
    if (navNotifications) {
      navNotifications.href = withWeek("/admin/notifications");
      navNotifications.hidden = navTier !== "admin";
    }
    if (navSentEmails) {
      navSentEmails.href = withWeek("/admin/sent-emails");
      navSentEmails.hidden = navTier !== "admin";
    }
    if (navForecast) {
      navForecast.href = withWeek("/forecast");
      navForecast.hidden = navTier !== "admin";
    }
  }

  if (appEl) {
    appEl.addEventListener("click", function (ev) {
      var rowBtn = ev.target && ev.target.closest ? ev.target.closest(".goals-row-checkin-btn") : null;
      if (rowBtn) {
        var gidx = parseInt(rowBtn.getAttribute("data-goal-idx"), 10);
        var ridx = parseInt(rowBtn.getAttribute("data-checkin-idx"), 10);
        if (Number.isFinite(gidx) && Number.isFinite(ridx)) {
          openCheckinDialog(gidx, ridx);
        }
      }
    });
  }

  if (checkinBackdrop) {
    checkinBackdrop.addEventListener("click", closeCheckinDialog);
  }
  if (checkinModalCancel) {
    checkinModalCancel.addEventListener("click", closeCheckinDialog);
  }
  if (checkinModalDismiss) {
    checkinModalDismiss.addEventListener("click", closeCheckinDialog);
  }
  if (checkinModalSave) {
    checkinModalSave.addEventListener("click", saveCheckinDialog);
  }
  if (checkinModalDelete) {
    checkinModalDelete.addEventListener("click", deleteCheckinDialog);
  }

  document.addEventListener("keydown", function (ev) {
    if (ev.key !== "Escape" || dialogGoalIndex < 0 || dialogCheckinIndex < 0) return;
    if (checkinModal && !checkinModal.hidden) {
      ev.preventDefault();
      closeCheckinDialog();
    }
  });

  fillYearSelect();
  if (!parseWeekKey(activeWeekKey)) activeWeekKey = toWeekKey(new Date());
  setupNav();

  if (yearEl) {
    yearEl.addEventListener("change", function () {
      selectedYear = parseInt(yearEl.value, 10) || selectedYear;
      setYearInUrl(selectedYear);
      fetchGoals();
    });
  }
  if (employeeEl) {
    employeeEl.addEventListener("change", fetchGoals);
  }
  if (refreshBtn) refreshBtn.addEventListener("click", fetchGoals);
  if (saveBtn) saveBtn.addEventListener("click", saveGoals);

  fetchGoals();
})();
