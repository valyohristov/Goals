(function () {
  "use strict";

  var appEl = document.getElementById("employees-app");
  var listEl = document.getElementById("employee-list");
  var statusEl = document.getElementById("employee-status");
  var searchEl = document.getElementById("employee-search");
  var addBtn = document.getElementById("employee-add");
  var refreshBtn = document.getElementById("employee-refresh");
  var drawerEl = document.getElementById("employee-drawer");
  var backdropEl = document.getElementById("employee-drawer-backdrop");
  var closeBtn = document.getElementById("employee-close");
  var drawerTitleEl = document.getElementById("employee-drawer-title");
  var formEl = document.getElementById("employee-form");
  var idEl = document.getElementById("employee-id");
  var nameEl = document.getElementById("employee-name");
  var managerEl = document.getElementById("employee-manager");
  var loginHintEl = document.getElementById("employee-login-hint");
  var usernameRowEl = document.getElementById("employee-login-username-row");
  var usernameEl = document.getElementById("employee-login-username");
  var passwordEl = document.getElementById("employee-password");
  var passwordConfirmEl = document.getElementById("employee-password-confirm");
  var capacityEl = document.getElementById("employee-capacity");
  var isManagerEl = document.getElementById("employee-is-manager");
  var deleteBtn = document.getElementById("employee-delete");
  var rowsWrapEl = document.getElementById("allocation-rows");
  var addRowBtn = document.getElementById("allocation-add-row");
  var weekPrevBtn = document.getElementById("employees-week-prev");
  var weekNextBtn = document.getElementById("employees-week-next");
  var weekLabelEl = document.getElementById("employees-week-label");
  var weekSubtitleEl = document.getElementById("employees-week-subtitle");
  var navHomeEl = document.getElementById("employees-nav-home");
  var navProjectsEl = document.getElementById("employees-nav-projects");
  var navNotificationsEl = document.getElementById("employees-nav-notifications");
  var navSentEmailsEl = document.getElementById("employees-nav-sent-emails");
  var navForecastEl = document.getElementById("employees-nav-forecast");
  var navGoalsEl = document.getElementById("employees-nav-goals");

  var cachedEmployees = [];
  var cachedManagerOptions = null;
  var cachedProjects = [];
  var activeWeekKey = (appEl && appEl.getAttribute("data-initial-week")) || "";
  var currentWeekKey = "";
  var drawerLoginUsernameOriginal = "";

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
      weekSubtitleEl.textContent = activeWeekKey === currentWeekKey
        ? "Current planning week"
        : "Future/past week snapshot (defaults from previous week until changed)";
    }
    if (navHomeEl) navHomeEl.href = withWeek("/");
    if (navProjectsEl) navProjectsEl.href = withWeek("/admin/projects");
    if (navNotificationsEl) navNotificationsEl.href = withWeek("/admin/notifications");
    if (navSentEmailsEl) navSentEmailsEl.href = withWeek("/admin/sent-emails");
    if (navForecastEl) navForecastEl.href = withWeek("/forecast");
    if (navGoalsEl) navGoalsEl.href = withWeek("/goals");
  }

  function managerOptionSource() {
    return cachedManagerOptions && cachedManagerOptions.length ? cachedManagerOptions : cachedEmployees;
  }

  function rebuildManagerOptions(editingEmployeeId) {
    if (!managerEl) return;
    var exclude = editingEmployeeId || "";
    var source = managerOptionSource();
    var opts = "<option value=\"\">None</option>" +
      source
        .filter(function (e) { return !exclude || e.id !== exclude; })
        .map(function (item) {
          return "<option value=\"" + escapeHtml(item.id) + "\">" + escapeHtml(item.name) + "</option>";
        })
        .join("");
    managerEl.innerHTML = opts;
  }

  function setLoginHint(mode) {
    if (!loginHintEl) return;
    if (mode === "add") {
      loginHintEl.textContent =
        "A login address firstname.lastname@domain is created when you save. Set password below if you want a non-default.";
      return;
    }
    if (passwordEl) {
      loginHintEl.textContent =
        "Change login username above. Optional: set a new password below.";
    } else {
      loginHintEl.textContent = "Ask an organization manager to change passwords or login usernames.";
    }
  }

  function clearPasswordFields() {
    if (passwordEl) passwordEl.value = "";
    if (passwordConfirmEl) passwordConfirmEl.value = "";
  }

  function clearDrawerLoginFields() {
    clearPasswordFields();
    if (usernameEl) usernameEl.value = "";
    drawerLoginUsernameOriginal = "";
  }

  function fillLoginUsername(mode, employee) {
    drawerLoginUsernameOriginal = "";
    if (usernameEl) usernameEl.value = "";
    if (!usernameEl || !usernameRowEl) return;
    usernameRowEl.hidden = mode !== "edit";
    if (mode !== "edit") return;
    var raw = employee && employee.loginUsername ? String(employee.loginUsername) : "";
    usernameEl.value = raw;
    drawerLoginUsernameOriginal = raw.trim().toLowerCase();
  }

  function escapeHtml(value) {
    var div = document.createElement("div");
    div.textContent = value == null ? "" : String(value);
    return div.innerHTML;
  }

  function setStatus(message, isError) {
    if (!statusEl) return;
    statusEl.textContent = message || "";
    statusEl.className = isError ? "text-danger" : "subtle";
  }

  function formatFte(value) {
    return Number(value || 0).toFixed(2).replace(/\.00$/, "");
  }

  function parseFte(value) {
    var raw = String(value == null ? "" : value).trim().replace(",", ".");
    var parsed = Number(raw);
    if (!isFinite(parsed) || parsed < 0) return 0;
    return Math.round(parsed * 100) / 100;
  }

  function rowTemplate(selectedId, fte) {
    var options = "<option value=\"\">Select project...</option>" +
      cachedProjects.map(function (item) {
        var selected = selectedId === item.id ? " selected" : "";
        return "<option value=\"" + escapeHtml(item.id) + "\"" + selected + ">" + escapeHtml(item.name) + "</option>";
      }).join("");

    return "<div class=\"allocation-row\">" +
      "<select class=\"allocation-project\">" + options + "</select>" +
      "<input class=\"allocation-fte\" type=\"number\" min=\"0\" step=\"0.1\" value=\"" + escapeHtml(fte || "") + "\" placeholder=\"FTE\" />" +
      "<button class=\"btn btn-danger allocation-remove\" type=\"button\">X</button>" +
      "</div>";
  }

  function addAllocationRow(selectedId, fte) {
    if (!rowsWrapEl) return;
    rowsWrapEl.insertAdjacentHTML("beforeend", rowTemplate(selectedId || "", fte || ""));
  }

  function readAllocationRows() {
    if (!rowsWrapEl) return [];
    var rows = Array.prototype.slice.call(rowsWrapEl.querySelectorAll(".allocation-row"));
    return rows.map(function (row) {
      var projectEl = row.querySelector(".allocation-project");
      var fteEl = row.querySelector(".allocation-fte");
      return {
        projectId: projectEl ? projectEl.value : "",
        fte: parseFte(fteEl ? fteEl.value : 0)
      };
    }).filter(function (item) {
      return item.projectId && item.fte > 0;
    });
  }

  function fillForm(employee, allocations) {
    var item = employee || { id: "", name: "", capacityFte: 1, isManager: false };
    if (idEl) idEl.value = item.id || "";
    if (nameEl) nameEl.value = item.name || "";
    if (capacityEl) capacityEl.value = formatFte(item.capacityFte || 1);
    if (isManagerEl) isManagerEl.checked = !!item.isManager;
    if (rowsWrapEl) rowsWrapEl.innerHTML = "";
    (allocations || []).forEach(function (allocation) {
      addAllocationRow(allocation.projectId, allocation.fte);
    });
    if (!allocations || allocations.length === 0) addAllocationRow("", "");
  }

  function openDrawer(mode, employee, allocations) {
    if (!drawerEl || !backdropEl) return;
    clearDrawerLoginFields();
    setLoginHint(mode);
    rebuildManagerOptions(mode === "edit" && employee && employee.id ? employee.id : "");
    fillForm(employee, allocations);
    fillLoginUsername(mode, employee);
    if (managerEl) {
      var mid = employee && employee.managerId ? employee.managerId : "";
      managerEl.value = mid;
    }
    if (drawerTitleEl) drawerTitleEl.textContent = mode === "edit" ? "Edit Employee" : "Add Employee";
    var canDel = appEl && appEl.getAttribute("data-can-delete-employees") === "true";
    if (deleteBtn) deleteBtn.hidden = mode !== "edit" || !canDel;
    drawerEl.hidden = false;
    backdropEl.hidden = false;
    document.body.classList.add("drawer-open");
    window.setTimeout(function () {
      drawerEl.classList.add("is-open");
      backdropEl.classList.add("is-open");
    }, 0);
  }

  function closeDrawer() {
    if (!drawerEl || !backdropEl) return;
    drawerEl.classList.remove("is-open");
    backdropEl.classList.remove("is-open");
    document.body.classList.remove("drawer-open");
    window.setTimeout(function () {
      drawerEl.hidden = true;
      backdropEl.hidden = true;
    }, 120);
  }

  function renderList(items) {
    if (!listEl) return;
    var list = Array.isArray(items) ? items : [];
    if (!list.length) {
      listEl.innerHTML = "<p class=\"subtle\">No employees found.</p>";
      return;
    }
    listEl.innerHTML = "<table class=\"data-table\"><thead><tr><th>Name</th><th>Username</th><th>Reports to</th><th>Mgr</th><th>Capacity</th></tr></thead><tbody>" +
      list.map(function (item) {
        var userLabel = item.loginUsername ? escapeHtml(item.loginUsername) : "<span class=\"subtle\">—</span>";
        var mgrLabel = item.managerName ? escapeHtml(item.managerName) : "<span class=\"subtle\">—</span>";
        var isMgr = item.isManager ? "Yes" : "<span class=\"subtle\">—</span>";
        return "<tr class=\"click-row\" data-id=\"" + escapeHtml(item.id) + "\"><td>" + escapeHtml(item.name) + "</td><td>" + userLabel + "</td><td>" + mgrLabel + "</td><td>" + isMgr + "</td><td>" + formatFte(item.capacityFte) + " FTE</td></tr>";
      }).join("") +
      "</tbody></table>";
  }

  function fetchProjects() {
    return fetch(withWeek("/api/projects"))
      .then(function (res) { return res.ok ? res.json() : Promise.reject(new Error("Could not load projects")); })
      .then(function (data) {
        if (data && data.weekKey) activeWeekKey = parseWeekKey(data.weekKey) || activeWeekKey;
        cachedProjects = Array.isArray(data.items) ? data.items : [];
      });
  }

  function fetchEmployees() {
    var q = searchEl && searchEl.value ? searchEl.value.trim() : "";
    var url = new URL(withWeek("/api/employees"), window.location.origin);
    if (q) url.searchParams.set("q", q);
    return fetch(url)
      .then(function (res) { return res.ok ? res.json() : Promise.reject(new Error("Could not load employees")); })
      .then(function (data) {
        if (data && data.weekKey) activeWeekKey = parseWeekKey(data.weekKey) || activeWeekKey;
        cachedEmployees = Array.isArray(data.items) ? data.items : [];
        cachedManagerOptions = Array.isArray(data.managerOptions) ? data.managerOptions : null;
        setWeekInUrl();
        refreshWeekUi();
        renderList(cachedEmployees);
      })
      .catch(function () {
        setStatus("Failed to load employees.", true);
      });
  }

  function fetchEmployeeAllocations(employeeId) {
    return fetch(withWeek("/api/employees/" + encodeURIComponent(employeeId) + "/allocations"))
      .then(function (res) { return res.ok ? res.json() : Promise.reject(new Error("Could not load allocations")); })
      .then(function (data) {
        return Array.isArray(data.allocations) ? data.allocations : [];
      });
  }

  function maybeSetUsername(employeeId) {
    if (!usernameEl || !usernameRowEl || usernameRowEl.hidden) return Promise.resolve();
    var raw = usernameEl.value ? usernameEl.value.trim() : "";
    if (!raw) {
      return Promise.reject(new Error("Login username cannot be empty."));
    }
    var normalized = raw.toLowerCase();
    if (normalized === drawerLoginUsernameOriginal) return Promise.resolve();
    return fetch(withWeek("/api/users/employee/" + encodeURIComponent(employeeId) + "/username"), {
      method: "PUT",
      headers: { "Content-Type": "application/json" },
      body: JSON.stringify({ username: raw })
    }).then(function (res) {
      if (res.ok) return res.json().then(function (data) {
        drawerLoginUsernameOriginal = data.username ? String(data.username).trim().toLowerCase() : normalized;
      });
      return res.json().then(function (payload) {
        throw new Error(payload && payload.error ? payload.error : "Could not set username");
      });
    });
  }

  function maybeSetPassword(employeeId) {
    var p1 = passwordEl && passwordEl.value ? passwordEl.value.trim() : "";
    var p2 = passwordConfirmEl && passwordConfirmEl.value ? passwordConfirmEl.value.trim() : "";
    if (!p1 && !p2) return Promise.resolve();
    if (p1 !== p2) return Promise.reject(new Error("Passwords do not match."));
    if (p1.length < 6) return Promise.reject(new Error("Password must be at least 6 characters."));
    return fetch(withWeek("/api/users/employee/" + encodeURIComponent(employeeId) + "/password"), {
      method: "PUT",
      headers: { "Content-Type": "application/json" },
      body: JSON.stringify({ password: p1 })
    }).then(function (res) {
      if (res.ok) return;
      return res.json().then(function (payload) {
        throw new Error(payload && payload.error ? payload.error : "Could not set password");
      });
    });
  }

  function saveEmployee() {
    var employee = {
      id: idEl && idEl.value || "",
      name: nameEl && nameEl.value.trim() || "",
      capacityFte: parseFte(capacityEl && capacityEl.value || 0)
    };
    if (!employee.name) {
      setStatus("Employee name is required.", true);
      return;
    }

    var managerId = managerEl && managerEl.value ? managerEl.value : "";
    var isUpdate = !!employee.id;
    var url = isUpdate ? withWeek("/api/employees/" + encodeURIComponent(employee.id)) : withWeek("/api/employees");
    var method = isUpdate ? "PUT" : "POST";

    fetch(url, {
      method: method,
      headers: { "Content-Type": "application/json" },
      body: JSON.stringify({
        name: employee.name,
        capacityFte: employee.capacityFte,
        managerId: managerId,
        isManager: !!(isManagerEl && isManagerEl.checked)
      })
    })
      .then(function (res) {
        if (res.ok) return res.json();
        return res.json().then(function (payload) {
          throw new Error(payload && payload.error ? payload.error : "Could not save employee");
        });
      })
      .then(function (saved) {
        return fetch(withWeek("/api/employees/" + encodeURIComponent(saved.id) + "/allocations"), {
          method: "PUT",
          headers: { "Content-Type": "application/json" },
          body: JSON.stringify({ allocations: readAllocationRows() })
        }).then(function (res) {
          if (!res.ok) return res.json().then(function (payload) {
            throw new Error(payload && payload.error ? payload.error : "Could not save allocations");
          });
          return saved;
        });
      })
      .then(function (saved) {
        return maybeSetUsername(saved.id).then(function () { return saved; });
      })
      .then(function (saved) {
        return maybeSetPassword(saved.id).then(function () { return saved; });
      })
      .then(function () {
        setStatus("Saved.");
        clearDrawerLoginFields();
        closeDrawer();
        fetchEmployees();
      })
      .catch(function (err) {
        setStatus(err && err.message ? err.message : "Save failed.", true);
      });
  }

  function deleteEmployee() {
    var id = idEl && idEl.value || "";
    if (!id) return;
    if (!window.confirm("Delete this employee and all related allocations?")) return;
    fetch(withWeek("/api/employees/" + encodeURIComponent(id)), { method: "DELETE" })
      .then(function (res) {
        if (res.ok) return res.json();
        return res.json().then(function (payload) {
          throw new Error(payload && payload.error ? payload.error : "Delete failed");
        });
      })
      .then(function () {
        setStatus("Deleted.");
        closeDrawer();
        fetchEmployees();
      })
      .catch(function (err) {
        setStatus(err && err.message ? err.message : "Delete failed.", true);
      });
  }

  if (formEl) {
    formEl.addEventListener("submit", function (event) {
      event.preventDefault();
      saveEmployee();
    });
  }

  if (addBtn) {
    if (appEl && appEl.getAttribute("data-can-add-employees") !== "true") {
      addBtn.hidden = true;
    } else {
      addBtn.addEventListener("click", function () {
        setStatus("");
        openDrawer("add", null, []);
      });
    }
  }

  if (refreshBtn) refreshBtn.addEventListener("click", fetchEmployees);
  if (closeBtn) closeBtn.addEventListener("click", closeDrawer);
  if (backdropEl) backdropEl.addEventListener("click", closeDrawer);
  if (deleteBtn) deleteBtn.addEventListener("click", deleteEmployee);

  if (addRowBtn) {
    addRowBtn.addEventListener("click", function () {
      addAllocationRow("", "");
    });
  }

  if (rowsWrapEl) {
    rowsWrapEl.addEventListener("click", function (event) {
      var target = event.target.closest(".allocation-remove");
      if (!target) return;
      var row = target.closest(".allocation-row");
      if (row) row.remove();
      if (!rowsWrapEl.querySelector(".allocation-row")) addAllocationRow("", "");
    });
  }

  if (searchEl) searchEl.addEventListener("input", fetchEmployees);
  if (weekPrevBtn) {
    weekPrevBtn.addEventListener("click", function () {
      activeWeekKey = shiftWeekKey(activeWeekKey, -1);
      setWeekInUrl();
      refreshWeekUi();
      Promise.all([fetchProjects(), fetchEmployees()]).catch(function () {
        setStatus("Failed to load selected week.", true);
      });
    });
  }
  if (weekNextBtn) {
    weekNextBtn.addEventListener("click", function () {
      activeWeekKey = shiftWeekKey(activeWeekKey, 1);
      setWeekInUrl();
      refreshWeekUi();
      Promise.all([fetchProjects(), fetchEmployees()]).catch(function () {
        setStatus("Failed to load selected week.", true);
      });
    });
  }
  document.addEventListener("keydown", function (event) {
    if (event.key === "Escape" && drawerEl && !drawerEl.hidden) closeDrawer();
  });

  if (listEl) {
    listEl.addEventListener("click", function (event) {
      var row = event.target.closest("tr.click-row[data-id]");
      if (!row) return;
      var id = row.getAttribute("data-id");
      var employee = cachedEmployees.find(function (item) { return item.id === id; });
      if (!employee) return;
      fetchEmployeeAllocations(id)
        .then(function (allocations) {
          openDrawer("edit", employee, allocations);
        })
        .catch(function () {
          setStatus("Could not load employee allocations.", true);
        });
    });
  }

  if (!parseWeekKey(activeWeekKey)) activeWeekKey = toWeekKey(new Date());
  currentWeekKey = toWeekKey(new Date());
  setWeekInUrl();
  refreshWeekUi();

  Promise.all([fetchProjects(), fetchEmployees()]).catch(function () {
    setStatus("Failed to initialize employee admin page.", true);
  });
})();
