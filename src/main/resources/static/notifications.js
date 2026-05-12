(function () {
  "use strict";

  var appEl = document.getElementById("notifications-app");
  var formEl = document.getElementById("notification-settings-form");
  var testFormEl = document.getElementById("notification-test-form");
  var statusEl = document.getElementById("notifications-status");

  var notificationsEnabledEl = document.getElementById("notifications-enabled");
  var tenantIdEl = document.getElementById("graph-tenant-id");
  var clientIdEl = document.getElementById("graph-client-id");
  var clientSecretEl = document.getElementById("graph-client-secret");
  var senderUserEl = document.getElementById("graph-sender-user");
  var fromNameEl = document.getElementById("from-name");
  var fromEmailEl = document.getElementById("from-email");
  var employeeSubjectEl = document.getElementById("employee-email-subject");
  var employeeBodyEl = document.getElementById("employee-email-body");
  var managerSubjectEl = document.getElementById("manager-email-subject");
  var managerBodyEl = document.getElementById("manager-email-body");
  var employeeJanGoalsSubjectEl = document.getElementById("employee-jan-goals-email-subject");
  var employeeJanGoalsBodyEl = document.getElementById("employee-jan-goals-email-body");
  var managerJanGoalsSubjectEl = document.getElementById("manager-jan-goals-email-subject");
  var managerJanGoalsBodyEl = document.getElementById("manager-jan-goals-email-body");
  var testToEmailEl = document.getElementById("test-to-email");

  var weekPrevBtn = document.getElementById("notifications-week-prev");
  var weekNextBtn = document.getElementById("notifications-week-next");
  var weekLabelEl = document.getElementById("notifications-week-label");
  var weekSubtitleEl = document.getElementById("notifications-week-subtitle");
  var navHomeEl = document.getElementById("notifications-nav-home");
  var navEmployeesEl = document.getElementById("notifications-nav-employees");
  var navProjectsEl = document.getElementById("notifications-nav-projects");
  var navSentEmailsEl = document.getElementById("notifications-nav-sent-emails");
  var navForecastEl = document.getElementById("notifications-nav-forecast");
  var navGoalsEl = document.getElementById("notifications-nav-goals");

  var activeWeekKey = (appEl && appEl.getAttribute("data-initial-week")) || "";
  var currentWeekKey = "";

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
    return "Week: " + startLabel + " - " + endLabel;
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

  function refreshWeekUi() {
    if (weekLabelEl) weekLabelEl.textContent = formatWeekLabel(activeWeekKey);
    if (weekSubtitleEl) {
      weekSubtitleEl.textContent =
        activeWeekKey === currentWeekKey ? "Current planning week" : "Selected week (navigation context)";
    }
    if (navHomeEl) navHomeEl.href = withWeek("/");
    if (navEmployeesEl) navEmployeesEl.href = withWeek("/admin/employees");
    if (navProjectsEl) navProjectsEl.href = withWeek("/admin/projects");
    if (navSentEmailsEl) navSentEmailsEl.href = withWeek("/admin/sent-emails");
    if (navForecastEl) navForecastEl.href = withWeek("/forecast");
    var toSentLog = document.getElementById("notifications-to-sent-log");
    if (toSentLog) toSentLog.href = withWeek("/admin/sent-emails");
    if (navGoalsEl) navGoalsEl.href = withWeek("/goals");
  }

  function setStatus(message, isError) {
    if (!statusEl) return;
    statusEl.textContent = message || "";
    statusEl.className = isError ? "text-danger" : "subtle";
  }

  function readError(res, fallback) {
    return res.json().then(
      function (payload) {
        throw new Error((payload && payload.error) || fallback);
      },
      function () {
        throw new Error(fallback);
      }
    );
  }

  function loadSettings() {
    return fetch("/api/admin/notification-settings")
      .then(function (res) {
        if (!res.ok) return readError(res, "Could not load notification settings.");
        return res.json();
      })
      .then(function (data) {
        if (notificationsEnabledEl) notificationsEnabledEl.checked = !!data.notificationsEnabled;
        if (tenantIdEl) tenantIdEl.value = data.tenantId || "";
        if (clientIdEl) clientIdEl.value = data.clientId || "";
        if (clientSecretEl) clientSecretEl.value = "";
        if (senderUserEl) senderUserEl.value = data.senderUser || "";
        if (fromNameEl) fromNameEl.value = data.fromName || "";
        if (fromEmailEl) fromEmailEl.value = data.fromEmail || "";
        if (employeeSubjectEl) employeeSubjectEl.value = data.employeeEmailSubject || "";
        if (employeeBodyEl) employeeBodyEl.value = data.employeeEmailBody || "";
        if (managerSubjectEl) managerSubjectEl.value = data.managerEmailSubject || "";
        if (managerBodyEl) managerBodyEl.value = data.managerEmailBody || "";
        if (employeeJanGoalsSubjectEl) employeeJanGoalsSubjectEl.value = data.employeeGoalSettingJanuarySubject || "";
        if (employeeJanGoalsBodyEl) employeeJanGoalsBodyEl.value = data.employeeGoalSettingJanuaryBody || "";
        if (managerJanGoalsSubjectEl) managerJanGoalsSubjectEl.value = data.managerGoalSettingJanuarySubject || "";
        if (managerJanGoalsBodyEl) managerJanGoalsBodyEl.value = data.managerGoalSettingJanuaryBody || "";
        setStatus("");
      })
      .catch(function (err) {
        setStatus((err && err.message) || "Could not load notification settings.", true);
      });
  }

  function saveSettings() {
    var payload = {
      notificationsEnabled: notificationsEnabledEl ? notificationsEnabledEl.checked : false,
      tenantId: tenantIdEl ? tenantIdEl.value.trim() : "",
      clientId: clientIdEl ? clientIdEl.value.trim() : "",
      clientSecret: clientSecretEl ? clientSecretEl.value : "",
      senderUser: senderUserEl ? senderUserEl.value.trim() : "",
      fromName: fromNameEl ? fromNameEl.value.trim() : "",
      fromEmail: fromEmailEl ? fromEmailEl.value.trim() : "",
      employeeEmailSubject: employeeSubjectEl ? employeeSubjectEl.value : "",
      employeeEmailBody: employeeBodyEl ? employeeBodyEl.value : "",
      managerEmailSubject: managerSubjectEl ? managerSubjectEl.value : "",
      managerEmailBody: managerBodyEl ? managerBodyEl.value : "",
      employeeGoalSettingJanuarySubject: employeeJanGoalsSubjectEl ? employeeJanGoalsSubjectEl.value : "",
      employeeGoalSettingJanuaryBody: employeeJanGoalsBodyEl ? employeeJanGoalsBodyEl.value : "",
      managerGoalSettingJanuarySubject: managerJanGoalsSubjectEl ? managerJanGoalsSubjectEl.value : "",
      managerGoalSettingJanuaryBody: managerJanGoalsBodyEl ? managerJanGoalsBodyEl.value : ""
    };

    return fetch("/api/admin/notification-settings", {
      method: "PUT",
      headers: { "Content-Type": "application/json" },
      body: JSON.stringify(payload)
    })
      .then(function (res) {
        if (!res.ok) return readError(res, "Could not save notification settings.");
        return res.json();
      })
      .then(function () {
        if (clientSecretEl) clientSecretEl.value = "";
        setStatus("Settings saved.");
      })
      .catch(function (err) {
        setStatus((err && err.message) || "Could not save notification settings.", true);
      });
  }

  function sendTestEmail() {
    var to = testToEmailEl ? testToEmailEl.value.trim() : "";
    if (!to) {
      setStatus("Recipient email is required.", true);
      return;
    }
    return fetch("/api/admin/notification-settings/test-email", {
      method: "POST",
      headers: { "Content-Type": "application/json" },
      body: JSON.stringify({ to: to })
    })
      .then(function (res) {
        if (!res.ok) return readError(res, "Test email failed.");
        return res.json();
      })
      .then(function () {
        setStatus("Test email sent.");
      })
      .catch(function (err) {
        setStatus((err && err.message) || "Test email failed.", true);
      });
  }

  if (formEl) {
    formEl.addEventListener("submit", function (event) {
      event.preventDefault();
      saveSettings();
    });
  }
  if (testFormEl) {
    testFormEl.addEventListener("submit", function (event) {
      event.preventDefault();
      sendTestEmail();
    });
  }

  if (weekPrevBtn) {
    weekPrevBtn.addEventListener("click", function () {
      activeWeekKey = shiftWeekKey(activeWeekKey, -1);
      setWeekInUrl();
      refreshWeekUi();
    });
  }
  if (weekNextBtn) {
    weekNextBtn.addEventListener("click", function () {
      activeWeekKey = shiftWeekKey(activeWeekKey, 1);
      setWeekInUrl();
      refreshWeekUi();
    });
  }

  if (!parseWeekKey(activeWeekKey)) activeWeekKey = toWeekKey(new Date());
  currentWeekKey = toWeekKey(new Date());
  setWeekInUrl();
  refreshWeekUi();
  loadSettings();
})();
