(function () {
  "use strict";

  var appEl = document.getElementById("projects-app");
  var listEl = document.getElementById("project-list");
  var statusEl = document.getElementById("project-status");
  var searchEl = document.getElementById("project-search");
  var addBtn = document.getElementById("project-add");
  var refreshBtn = document.getElementById("project-refresh");
  var drawerEl = document.getElementById("project-drawer");
  var backdropEl = document.getElementById("project-drawer-backdrop");
  var closeBtn = document.getElementById("project-close");
  var drawerTitleEl = document.getElementById("project-drawer-title");
  var formEl = document.getElementById("project-form");
  var idEl = document.getElementById("project-id");
  var nameEl = document.getElementById("project-name");
  var capacityEl = document.getElementById("project-capacity");
  var colorEl = document.getElementById("project-color");
  var deleteBtn = document.getElementById("project-delete");
  var weekPrevBtn = document.getElementById("projects-week-prev");
  var weekNextBtn = document.getElementById("projects-week-next");
  var weekLabelEl = document.getElementById("projects-week-label");
  var weekSubtitleEl = document.getElementById("projects-week-subtitle");
  var navHomeEl = document.getElementById("projects-nav-home");
  var navEmployeesEl = document.getElementById("projects-nav-employees");
  var navNotificationsEl = document.getElementById("projects-nav-notifications");
  var navSentEmailsEl = document.getElementById("projects-nav-sent-emails");
  var navForecastEl = document.getElementById("projects-nav-forecast");
  var navGoalsEl = document.getElementById("projects-nav-goals");

  var cachedProjects = [];
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
      weekSubtitleEl.textContent = activeWeekKey === currentWeekKey
        ? "Current planning week"
        : "Future/past week snapshot (defaults from previous week until changed)";
    }
    if (navHomeEl) navHomeEl.href = withWeek("/");
    if (navEmployeesEl) navEmployeesEl.href = withWeek("/admin/employees");
    if (navNotificationsEl) navNotificationsEl.href = withWeek("/admin/notifications");
    if (navSentEmailsEl) navSentEmailsEl.href = withWeek("/admin/sent-emails");
    if (navForecastEl) navForecastEl.href = withWeek("/forecast");
    if (navGoalsEl) navGoalsEl.href = withWeek("/goals");
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

  function normalizeColor(value) {
    var color = String(value || "").trim().toLowerCase();
    return /^#[0-9a-f]{6}$/.test(color) ? color : "#8b5cf6";
  }

  function fillForm(project) {
    var item = project || { id: "", name: "", capacityFte: 1, color: "#8b5cf6" };
    if (idEl) idEl.value = item.id || "";
    if (nameEl) nameEl.value = item.name || "";
    if (capacityEl) capacityEl.value = formatFte(item.capacityFte || 1);
    if (colorEl) colorEl.value = normalizeColor(item.color);
  }

  function openDrawer(mode, project) {
    if (!drawerEl || !backdropEl) return;
    fillForm(project);
    if (drawerTitleEl) drawerTitleEl.textContent = mode === "edit" ? "Edit Project" : "Add Project";
    if (deleteBtn) deleteBtn.hidden = mode !== "edit";
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
      listEl.innerHTML = "<p class=\"subtle\">No projects found.</p>";
      return;
    }
    listEl.innerHTML = "<table class=\"data-table\"><thead><tr><th>Color</th><th>Name</th><th>Capacity</th></tr></thead><tbody>" +
      list.map(function (item) {
        var color = normalizeColor(item.color);
        return "<tr class=\"click-row\" data-id=\"" + escapeHtml(item.id) + "\">" +
          "<td><span class=\"color-dot\" style=\"background:" + escapeHtml(color) + ";\"></span> <code>" + escapeHtml(color) + "</code></td>" +
          "<td>" + escapeHtml(item.name) + "</td>" +
          "<td>" + formatFte(item.capacityFte) + " FTE</td>" +
          "</tr>";
      }).join("") +
      "</tbody></table>";
  }

  function fetchProjects() {
    var q = searchEl && searchEl.value ? searchEl.value.trim() : "";
    var url = new URL(withWeek("/api/projects"), window.location.origin);
    if (q) url.searchParams.set("q", q);
    return fetch(url)
      .then(function (res) { return res.ok ? res.json() : Promise.reject(new Error("Could not load projects")); })
      .then(function (data) {
        if (data && data.weekKey) activeWeekKey = parseWeekKey(data.weekKey) || activeWeekKey;
        setWeekInUrl();
        refreshWeekUi();
        cachedProjects = Array.isArray(data.items) ? data.items : [];
        renderList(cachedProjects);
      })
      .catch(function () {
        setStatus("Failed to load projects.", true);
      });
  }

  function saveProject() {
    var project = {
      id: idEl && idEl.value || "",
      name: nameEl && nameEl.value.trim() || "",
      capacityFte: parseFte(capacityEl && capacityEl.value || 0),
      color: normalizeColor(colorEl && colorEl.value)
    };
    if (!project.name) {
      setStatus("Project name is required.", true);
      return;
    }

    var isUpdate = !!project.id;
    var url = isUpdate ? withWeek("/api/projects/" + encodeURIComponent(project.id)) : withWeek("/api/projects");
    var method = isUpdate ? "PUT" : "POST";
    fetch(url, {
      method: method,
      headers: { "Content-Type": "application/json" },
      body: JSON.stringify({
        name: project.name,
        capacityFte: project.capacityFte,
        color: project.color
      })
    })
      .then(function (res) {
        if (res.ok) return res.json();
        return res.json().then(function (payload) {
          throw new Error(payload && payload.error ? payload.error : "Save failed");
        });
      })
      .then(function () {
        setStatus("Saved.");
        closeDrawer();
        fetchProjects();
      })
      .catch(function (err) {
        setStatus(err && err.message ? err.message : "Save failed.", true);
      });
  }

  function deleteProject() {
    var id = idEl && idEl.value || "";
    if (!id) return;
    if (!window.confirm("Delete this project and all related allocations?")) return;
    fetch(withWeek("/api/projects/" + encodeURIComponent(id)), { method: "DELETE" })
      .then(function (res) {
        if (res.ok) return res.json();
        return res.json().then(function (payload) {
          throw new Error(payload && payload.error ? payload.error : "Delete failed");
        });
      })
      .then(function () {
        setStatus("Deleted.");
        closeDrawer();
        fetchProjects();
      })
      .catch(function (err) {
        setStatus(err && err.message ? err.message : "Delete failed.", true);
      });
  }

  if (formEl) {
    formEl.addEventListener("submit", function (event) {
      event.preventDefault();
      saveProject();
    });
  }
  if (addBtn) addBtn.addEventListener("click", function () { setStatus(""); openDrawer("add", null); });
  if (refreshBtn) refreshBtn.addEventListener("click", fetchProjects);
  if (searchEl) searchEl.addEventListener("input", fetchProjects);
  if (weekPrevBtn) {
    weekPrevBtn.addEventListener("click", function () {
      activeWeekKey = shiftWeekKey(activeWeekKey, -1);
      setWeekInUrl();
      refreshWeekUi();
      fetchProjects();
    });
  }
  if (weekNextBtn) {
    weekNextBtn.addEventListener("click", function () {
      activeWeekKey = shiftWeekKey(activeWeekKey, 1);
      setWeekInUrl();
      refreshWeekUi();
      fetchProjects();
    });
  }
  if (closeBtn) closeBtn.addEventListener("click", closeDrawer);
  if (backdropEl) backdropEl.addEventListener("click", closeDrawer);
  if (deleteBtn) deleteBtn.addEventListener("click", deleteProject);

  document.addEventListener("keydown", function (event) {
    if (event.key === "Escape" && drawerEl && !drawerEl.hidden) closeDrawer();
  });

  if (listEl) {
    listEl.addEventListener("click", function (event) {
      var row = event.target.closest("tr.click-row[data-id]");
      if (!row) return;
      var id = row.getAttribute("data-id");
      var project = cachedProjects.find(function (item) { return item.id === id; });
      if (!project) return;
      openDrawer("edit", project);
    });
  }

  if (!parseWeekKey(activeWeekKey)) activeWeekKey = toWeekKey(new Date());
  currentWeekKey = toWeekKey(new Date());
  setWeekInUrl();
  refreshWeekUi();
  fetchProjects();
})();
