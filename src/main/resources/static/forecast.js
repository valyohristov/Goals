(function () {
  "use strict";

  var appEl = document.getElementById("forecast-app");
  var weekPrevBtn = document.getElementById("forecast-week-prev");
  var weekNextBtn = document.getElementById("forecast-week-next");
  var weekLabelEl = document.getElementById("forecast-week-label");
  var summaryEl = document.getElementById("forecast-summary");
  var projectIssuesEl = document.getElementById("forecast-project-issues");
  var barsEl = document.getElementById("forecast-bars");
  var tableEl = document.getElementById("forecast-table");
  var navHomeEl = document.getElementById("forecast-nav-home");
  var navEmployeesEl = document.getElementById("forecast-nav-employees");
  var navProjectsEl = document.getElementById("forecast-nav-projects");
  var navNotificationsEl = document.getElementById("forecast-nav-notifications");
  var navSentEmailsEl = document.getElementById("forecast-nav-sent-emails");
  var navGoalsEl = document.getElementById("forecast-nav-goals");

  var activeWeekKey = (appEl && appEl.getAttribute("data-initial-week")) || "";
  var horizonWeeks = 13;

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

  function withWeek(path) {
    var parsed = parseWeekKey(activeWeekKey);
    var url = new URL(path, window.location.origin);
    if (parsed) url.searchParams.set("week", parsed);
    return url.pathname + url.search;
  }

  function setWeekInUrl() {
    var parsed = parseWeekKey(activeWeekKey);
    if (!parsed) return;
    var url = new URL(window.location.href);
    url.searchParams.set("week", parsed);
    window.history.replaceState(null, "", url.toString());
  }

  function formatWeekLabel(weekKey) {
    var parsed = parseWeekKey(weekKey);
    if (!parsed) return "Week";
    var start = new Date(parsed + "T00:00:00Z");
    var end = new Date(start.getTime());
    end.setUTCDate(end.getUTCDate() + 6);
    var startLabel = start.toLocaleDateString(undefined, { month: "short", day: "numeric", year: "numeric" });
    var endLabel = end.toLocaleDateString(undefined, { month: "short", day: "numeric", year: "numeric" });
    return startLabel + " - " + endLabel;
  }

  function formatFte(value) {
    var n = Number(value || 0);
    return n.toFixed(2).replace(/\.00$/, "") + " FTE";
  }

  function escapeHtml(value) {
    var div = document.createElement("div");
    div.textContent = value == null ? "" : String(value);
    return div.innerHTML;
  }

  function refreshNav() {
    if (navHomeEl) navHomeEl.href = withWeek("/");
    if (navEmployeesEl) navEmployeesEl.href = withWeek("/admin/employees");
    if (navProjectsEl) navProjectsEl.href = withWeek("/admin/projects");
    if (navNotificationsEl) navNotificationsEl.href = withWeek("/admin/notifications");
    if (navSentEmailsEl) navSentEmailsEl.href = withWeek("/admin/sent-emails");
    if (navGoalsEl) navGoalsEl.href = withWeek("/goals");
  }

  function renderSummary(data) {
    if (!summaryEl) return;
    var s = data && data.summary ? data.summary : {};
    summaryEl.innerHTML =
      "<article class=\"card\"><h3>Average Utilization</h3><p>" + (s.avgUtilizationPct || 0) + "%</p></article>" +
      "<article class=\"card\"><h3>Weeks At Risk</h3><p>" + (s.riskWeeks || 0) + "</p></article>" +
      "<article class=\"card\"><h3>Balanced Weeks</h3><p>" + (s.balancedWeeks || 0) + "</p></article>";
  }

  function renderProjectIssues(data) {
    if (!projectIssuesEl) return;
    var issues = data && data.projectIssues ? data.projectIssues : {};
    var over = Array.isArray(issues.overallocated) ? issues.overallocated : [];
    var under = Array.isArray(issues.understaffed) ? issues.understaffed : [];

    function renderList(items, type) {
      if (!items.length) {
        return "<p class=\"subtle\">None</p>";
      }
      return "<ul class=\"issue-list\">" + items.map(function (item) {
        var deltaLabel = type === "over"
          ? ("+" + item.deltaFte + " FTE")
          : ("-" + item.deltaFte + " FTE");
        return "<li><strong>" + escapeHtml(item.name) + "</strong><span>" +
          escapeHtml(deltaLabel) + " (" +
          escapeHtml(formatFte(item.allocatedFte)) + " / " + escapeHtml(formatFte(item.capacityFte)) +
          ")</span></li>";
      }).join("") + "</ul>";
    }

    projectIssuesEl.innerHTML =
      "<div class=\"issue-grid\">" +
        "<article class=\"issue-card over\">" +
          "<h3>Overallocated Projects</h3>" +
          renderList(over, "over") +
        "</article>" +
        "<article class=\"issue-card under\">" +
          "<h3>Understaffed Projects</h3>" +
          renderList(under, "under") +
        "</article>" +
      "</div>";
  }

  function renderBars(data) {
    if (!barsEl) return;
    var weeks = data && Array.isArray(data.weeks) ? data.weeks : [];
    if (!weeks.length) {
      barsEl.innerHTML = "<p class=\"subtle\">No forecast data.</p>";
      return;
    }
    barsEl.innerHTML = weeks.map(function (item) {
      var pct = Math.max(0, Math.min(180, item.utilizationPct || 0));
      var cls = item.utilizationPct > 100 ? "forecast-bar over" : (item.utilizationPct < 85 ? "forecast-bar under" : "forecast-bar ok");
      return "<div class=\"forecast-row\">" +
        "<div class=\"forecast-week\">" + escapeHtml(formatWeekLabel(item.weekKey)) + "</div>" +
        "<div class=\"forecast-track\"><div class=\"" + cls + "\" style=\"width:" + pct + "%\"></div></div>" +
        "<div class=\"forecast-value\">" + (item.utilizationPct || 0) + "%</div>" +
      "</div>";
    }).join("");
  }

  function renderTable(data) {
    if (!tableEl) return;
    var weeks = data && Array.isArray(data.weeks) ? data.weeks : [];
    if (!weeks.length) {
      tableEl.innerHTML = "<p class=\"subtle\">No forecast data.</p>";
      return;
    }
    tableEl.innerHTML = "<table class=\"data-table\"><thead><tr>" +
      "<th>Week</th><th>Employee Load</th><th>Project Load</th><th>Issues</th><th>Status</th>" +
      "</tr></thead><tbody>" +
      weeks.map(function (item) {
        var statusClass = item.status === "Balanced" ? "forecast-status ok" : "forecast-status warn";
        return "<tr>" +
          "<td>" + escapeHtml(formatWeekLabel(item.weekKey)) + "</td>" +
          "<td>" + escapeHtml(formatFte(item.employeeAllocated)) + " / " + escapeHtml(formatFte(item.employeeCapacity)) + "</td>" +
          "<td>" + escapeHtml(formatFte(item.projectAllocated)) + " / " + escapeHtml(formatFte(item.projectCapacity)) + "</td>" +
          "<td>" + item.overloadedEmployees + " overloaded employees, " + item.understaffedProjects + " understaffed projects</td>" +
          "<td><span class=\"" + statusClass + "\">" + escapeHtml(item.status) + "</span></td>" +
          "</tr>";
      }).join("") +
      "</tbody></table>";
  }

  function load() {
    var url = new URL(withWeek("/api/forecast"), window.location.origin);
    url.searchParams.set("weeks", String(horizonWeeks));
    fetch(url)
      .then(function (res) {
        if (!res.ok) throw new Error("Could not load forecast");
        return res.json();
      })
      .then(function (data) {
        activeWeekKey = parseWeekKey(data && data.startWeekKey) || activeWeekKey || toWeekKey(new Date());
        setWeekInUrl();
        refreshNav();
        if (weekLabelEl) {
          weekLabelEl.textContent = formatWeekLabel(data.startWeekKey) + " to " + formatWeekLabel(data.endWeekKey);
        }
        renderSummary(data);
        renderProjectIssues(data);
        renderBars(data);
        renderTable(data);
      })
      .catch(function () {
        if (tableEl) tableEl.innerHTML = "<p class=\"text-danger\">Failed to load forecast data.</p>";
      });
  }

  if (!parseWeekKey(activeWeekKey)) activeWeekKey = toWeekKey(new Date());
  setWeekInUrl();
  refreshNav();

  if (weekPrevBtn) weekPrevBtn.addEventListener("click", function () {
    activeWeekKey = shiftWeekKey(activeWeekKey, -1);
    load();
  });
  if (weekNextBtn) weekNextBtn.addEventListener("click", function () {
    activeWeekKey = shiftWeekKey(activeWeekKey, 1);
    load();
  });

  load();
})();
