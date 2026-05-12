(function () {
  "use strict";

  var appEl = document.getElementById("home-app");
  var summaryEl = document.getElementById("summary-cards");
  var sankeyWrapEl = document.getElementById("sankey-wrap");
  var utilizationEl = document.getElementById("utilization-table");
  var weekPrevBtn = document.getElementById("week-prev");
  var weekNextBtn = document.getElementById("week-next");
  var weekLabelEl = document.getElementById("week-label");
  var weekSubtitleEl = document.getElementById("week-subtitle");
  var navEmployeesEl = document.getElementById("nav-employees");
  var navProjectsEl = document.getElementById("nav-projects");
  var navNotificationsEl = document.getElementById("nav-notifications");
  var navSentEmailsEl = document.getElementById("nav-sent-emails");
  var navForecastEl = document.getElementById("nav-forecast");
  var navGoalsEl = document.getElementById("nav-goals");
  var weekCopyToggleEl = document.getElementById("week-copy-toggle");
  var allocationBackdropEl = document.getElementById("home-allocation-backdrop");
  var allocationDrawerEl = document.getElementById("home-allocation-drawer");
  var allocationTitleEl = document.getElementById("home-allocation-title");
  var allocationWeekLabelEl = document.getElementById("home-allocation-week-label");
  var allocationCloseBtn = document.getElementById("home-allocation-close");
  var allocationFormEl = document.getElementById("home-allocation-form");
  var allocationRowsEl = document.getElementById("home-allocation-rows");
  var allocationAddRowBtn = document.getElementById("home-allocation-add-row");

  var activeWeekKey = (appEl && appEl.getAttribute("data-initial-week")) || "";
  var readOnly = appEl && appEl.getAttribute("data-read-only") === "true";
  var currentWeekKey = "";
  var isSavingCapacity = false;
  var isSavingAllocations = false;
  var isSavingWeekSettings = false;
  var latestData = null;
  var editingEmployeeId = "";

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

  function setWeekInUrl(weekKey) {
    var parsed = parseWeekKey(weekKey);
    if (!parsed) return;
    var url = new URL(window.location.href);
    url.searchParams.set("week", parsed);
    window.history.replaceState(null, "", url.toString());
  }

  function withWeek(path, weekKey) {
    var parsed = parseWeekKey(weekKey);
    if (!parsed) return path;
    var url = new URL(path, window.location.origin);
    url.searchParams.set("week", parsed);
    return url.pathname + url.search;
  }

  function refreshWeekUi() {
    if (weekLabelEl) weekLabelEl.textContent = formatWeekLabel(activeWeekKey);
    if (weekSubtitleEl) {
      weekSubtitleEl.textContent = activeWeekKey === currentWeekKey
        ? "Current planning week"
        : "Future/past week snapshot (defaults from previous week until changed)";
    }
    if (navEmployeesEl) navEmployeesEl.href = withWeek("/admin/employees", activeWeekKey);
    if (navProjectsEl) navProjectsEl.href = withWeek("/admin/projects", activeWeekKey);
    if (navNotificationsEl) navNotificationsEl.href = withWeek("/admin/notifications", activeWeekKey);
    if (navSentEmailsEl) navSentEmailsEl.href = withWeek("/admin/sent-emails", activeWeekKey);
    if (navForecastEl) navForecastEl.href = withWeek("/forecast", activeWeekKey);
    if (navGoalsEl) navGoalsEl.href = withWeek("/goals", activeWeekKey);
  }

  function updateWeekCopyToggleState(isEnabled) {
    if (!weekCopyToggleEl) return;
    weekCopyToggleEl.checked = !!isEnabled;
    weekCopyToggleEl.disabled = !!isSavingWeekSettings;
  }

  function formatFte(value) {
    var n = Number(value || 0);
    return n.toFixed(2).replace(/\.00$/, "") + " FTE";
  }

  function parseFte(value) {
    var raw = String(value == null ? "" : value).trim().replace(",", ".");
    var parsed = Number(raw);
    if (!isFinite(parsed) || parsed < 0) return NaN;
    return Math.round(parsed * 100) / 100;
  }

  function escapeHtml(value) {
    var div = document.createElement("div");
    div.textContent = value == null ? "" : String(value);
    return div.innerHTML;
  }

  function renderSummary(data) {
    if (!summaryEl) return;
    var totals = (data && data.totals) || {};
    summaryEl.innerHTML =
      "<article class=\"card\"><h3>Employee Capacity</h3><p>" + formatFte(totals.employeeCapacity) + "</p></article>" +
      "<article class=\"card\"><h3>Allocated</h3><p>" + formatFte(totals.employeeAllocated) + "</p></article>" +
      "<article class=\"card\"><h3>Project Capacity</h3><p>" + formatFte(totals.projectCapacity) + "</p></article>";
  }

  function renderUtilizationTable(data) {
    if (!utilizationEl) return;
    var rows = (data && data.employeeUtilization) || [];
    if (!rows.length) {
      utilizationEl.innerHTML = "<p class=\"subtle\">No employees configured.</p>";
      return;
    }

    utilizationEl.innerHTML = "<table class=\"data-table\"><thead><tr><th>Employee</th><th>Capacity</th><th>Allocated</th><th>Remaining</th></tr></thead><tbody>" +
      rows.map(function (item) {
        var remaining = (item.capacityFte || 0) - (item.allocatedFte || 0);
        var statusClass = remaining < 0 ? "text-danger" : "";
        return "<tr>" +
          "<td>" + escapeHtml(item.name) + "</td>" +
          "<td>" + formatFte(item.capacityFte) + "</td>" +
          "<td>" + formatFte(item.allocatedFte) + "</td>" +
          "<td class=\"" + statusClass + "\">" + formatFte(remaining) + "</td>" +
          "</tr>";
      }).join("") +
      "</tbody></table>";
  }

  function editNodeCapacity(nodeData) {
    if (readOnly) return;
    if (nodeData && nodeData.type === "project" && appEl && appEl.getAttribute("data-can-edit-projects") !== "true") {
      window.alert("Only organization managers can change project capacity. Adjust allocations using the links, or ask an admin.");
      return;
    }
    if (!nodeData || !nodeData.id || !nodeData.type) return;
    if (isSavingCapacity) return;
    var current = Number(nodeData.capacityFte || 0);
    var label = nodeData.type === "employee" ? "employee" : "project";
    var answer = window.prompt("Set weekly capacity (FTE) for " + label + " \"" + nodeData.name + "\"", String(current));
    if (answer === null) return;
    var next = parseFte(answer);
    if (!Number.isFinite(next)) {
      window.alert("Please enter a valid non-negative number.");
      return;
    }

    var endpoint = nodeData.type === "employee"
      ? "/api/employees/" + encodeURIComponent(nodeData.id)
      : "/api/projects/" + encodeURIComponent(nodeData.id);
    isSavingCapacity = true;
    fetch(withWeek(endpoint, activeWeekKey), {
      method: "PUT",
      headers: { "Content-Type": "application/json" },
      body: JSON.stringify({ capacityFte: Math.round(next * 100) / 100 })
    })
      .then(function (res) {
        if (!res.ok) {
          return res.json()
            .then(function (payload) {
              throw new Error(payload && payload.error ? payload.error : "Could not update capacity");
            })
            .catch(function () {
              throw new Error("Could not update capacity");
            });
        }
        return res.json();
      })
      .then(function () {
        load();
      })
      .catch(function (error) {
        window.alert((error && error.message) || "Failed to update capacity.");
      })
      .finally(function () {
        isSavingCapacity = false;
      });
  }

  function buildAllocationRow(projects, selectedProjectId, fte) {
    var options = "<option value=\"\">Select project...</option>" +
      projects.map(function (project) {
        var selected = selectedProjectId === project.id ? " selected" : "";
        return "<option value=\"" + escapeHtml(project.id) + "\"" + selected + ">" + escapeHtml(project.name) + "</option>";
      }).join("");

    return "<div class=\"allocation-row\">" +
      "<select class=\"allocation-project\">" + options + "</select>" +
      "<input class=\"allocation-fte\" type=\"number\" min=\"0\" step=\"0.1\" value=\"" + escapeHtml(fte || "") + "\" placeholder=\"FTE\" />" +
      "<button class=\"btn btn-danger allocation-remove\" type=\"button\">X</button>" +
      "</div>";
  }

  function addAllocationRow(projects, selectedProjectId, fte) {
    if (!allocationRowsEl) return;
    allocationRowsEl.insertAdjacentHTML("beforeend", buildAllocationRow(projects, selectedProjectId, fte));
  }

  function openAllocationDrawer() {
    if (!allocationDrawerEl || !allocationBackdropEl) return;
    allocationDrawerEl.hidden = false;
    allocationBackdropEl.hidden = false;
    document.body.classList.add("drawer-open");
    window.setTimeout(function () {
      allocationDrawerEl.classList.add("is-open");
      allocationBackdropEl.classList.add("is-open");
    }, 0);
  }

  function closeAllocationDrawer() {
    if (!allocationDrawerEl || !allocationBackdropEl) return;
    allocationDrawerEl.classList.remove("is-open");
    allocationBackdropEl.classList.remove("is-open");
    document.body.classList.remove("drawer-open");
    window.setTimeout(function () {
      allocationDrawerEl.hidden = true;
      allocationBackdropEl.hidden = true;
    }, 120);
  }

  function getEmployeeAllocationsFromData(employeeId, data) {
    var links = data && data.links ? data.links : [];
    return links
      .filter(function (item) { return item.employeeId === employeeId; })
      .map(function (item) {
        return { projectId: item.projectId, fte: item.value };
      });
  }

  function openLinkEditor(linkData) {
    if (readOnly) return;
    if (!latestData || !linkData || !linkData.employeeId) return;
    var employee = (latestData.employees || []).find(function (item) { return item.id === linkData.employeeId; });
    if (!employee) return;

    editingEmployeeId = employee.id;
    var projects = latestData.projects || [];
    var allocations = getEmployeeAllocationsFromData(employee.id, latestData);

    if (allocationTitleEl) allocationTitleEl.textContent = "Allocations: " + employee.name;
    if (allocationWeekLabelEl) allocationWeekLabelEl.textContent = formatWeekLabel(activeWeekKey);
    if (allocationRowsEl) allocationRowsEl.innerHTML = "";

    allocations.forEach(function (item) {
      addAllocationRow(projects, item.projectId, item.fte);
    });
    if (!allocations.length) addAllocationRow(projects, linkData.projectId || "", "");
    openAllocationDrawer();
  }

  function readAllocationRows() {
    if (!allocationRowsEl) return [];
    var rows = Array.prototype.slice.call(allocationRowsEl.querySelectorAll(".allocation-row"));
    return rows
      .map(function (row) {
        var projectEl = row.querySelector(".allocation-project");
        var fteEl = row.querySelector(".allocation-fte");
        return {
          projectId: projectEl ? projectEl.value : "",
          fte: parseFte(fteEl ? fteEl.value : 0) || 0
        };
      })
      .filter(function (item) { return item.projectId && item.fte > 0; });
  }

  function saveEmployeeAllocations() {
    if (!editingEmployeeId || isSavingAllocations) return;
    var rows = readAllocationRows();
    var dedup = [];
    var seen = {};
    rows.forEach(function (item) {
      if (seen[item.projectId]) return;
      seen[item.projectId] = true;
      dedup.push(item);
    });

    isSavingAllocations = true;
    fetch(withWeek("/api/employees/" + encodeURIComponent(editingEmployeeId) + "/allocations", activeWeekKey), {
      method: "PUT",
      headers: { "Content-Type": "application/json" },
      body: JSON.stringify({ allocations: dedup })
    })
      .then(function (res) {
        if (!res.ok) {
          return res.json()
            .then(function (payload) {
              throw new Error(payload && payload.error ? payload.error : "Could not update allocations");
            })
            .catch(function () {
              throw new Error("Could not update allocations");
            });
        }
        return res.json();
      })
      .then(function () {
        closeAllocationDrawer();
        load();
      })
      .catch(function (error) {
        window.alert((error && error.message) || "Failed to save allocations.");
      })
      .finally(function () {
        isSavingAllocations = false;
      });
  }

  function renderSankey(data) {
    if (!sankeyWrapEl) return;
    sankeyWrapEl.innerHTML = "";

    var nodes = (data && data.nodes) || [];
    var links = (data && data.links) || [];

    if (data && data.scope === "team") {
      if (!data.employees || !data.employees.length) {
        sankeyWrapEl.innerHTML = "<p class=\"subtle\">No team members in this week plan.</p>";
        return;
      }
      if (!links.length) {
        sankeyWrapEl.innerHTML = "<p class=\"subtle\">Your team has no project allocations for this week.</p>";
        return;
      }
    }

    if (data && data.scope === "employee") {
      if (!data.employees || !data.employees.length) {
        sankeyWrapEl.innerHTML = "<p class=\"subtle\">You are not part of this week plan.</p>";
        return;
      }
      if (!links.length) {
        sankeyWrapEl.innerHTML = "<p class=\"subtle\">You have no project allocations for this week.</p>";
        return;
      }
    }

    if (!nodes.length || !links.length) {
      sankeyWrapEl.innerHTML = "<p class=\"subtle\">Add employees, projects and allocations to display the Sankey diagram.</p>";
      return;
    }

    var width = Math.max(760, sankeyWrapEl.clientWidth || 760);
    var height = Math.max(420, nodes.length * 36);
    var color = d3.scaleOrdinal()
      .domain(["employee", "project"])
      .range(["#3b82f6", "#8b5cf6"]);

    var graph = {
      nodes: nodes.map(function (d) { return Object.assign({}, d); }),
      links: links.map(function (d) { return Object.assign({}, d); })
    };

    var sankey = d3.sankey()
      .nodeWidth(16)
      .nodePadding(14)
      .extent([[8, 8], [width - 8, height - 8]]);

    sankey(graph);

    var svg = d3.select(sankeyWrapEl).append("svg")
      .attr("viewBox", "0 0 " + width + " " + height)
      .attr("class", "sankey-svg");

    var linkTitle = readOnly ? function (d) { return d.value + " FTE"; } : function (d) { return d.value + " FTE - click to edit allocations"; };
    var nodeTitle = readOnly
      ? function (d) { return d.name + " (" + formatFte(d.capacityFte) + ")"; }
      : function (d) { return d.name + " (" + formatFte(d.capacityFte) + ") - click to edit"; };

    svg.append("g")
      .attr("fill", "none")
      .selectAll("path")
      .data(graph.links)
      .join("path")
      .attr("d", d3.sankeyLinkHorizontal())
      .attr("stroke", function (d) { return d.projectColor || "#9ca3af"; })
      .attr("stroke-opacity", 0.45)
      .attr("stroke-width", function (d) { return Math.max(1, d.width); })
      .attr("class", "sankey-link")
      .on("click", function (_event, d) {
        if (!readOnly) openLinkEditor(d);
      })
      .append("title")
      .text(linkTitle);

    var node = svg.append("g")
      .selectAll("g")
      .data(graph.nodes)
      .join("g");

    node.append("rect")
      .attr("x", function (d) { return d.x0; })
      .attr("y", function (d) { return d.y0; })
      .attr("height", function (d) { return d.y1 - d.y0; })
      .attr("width", function (d) { return d.x1 - d.x0; })
      .attr("fill", function (d) { return color(d.type); })
      .attr("class", "sankey-node-rect")
      .on("click", function (_event, d) {
        if (!readOnly) editNodeCapacity(d);
      })
      .append("title")
      .text(nodeTitle);

    node.append("text")
      .attr("x", function (d) { return d.x0 < width / 2 ? d.x1 + 8 : d.x0 - 8; })
      .attr("y", function (d) { return (d.y1 + d.y0) / 2; })
      .attr("dy", "0.35em")
      .attr("text-anchor", function (d) { return d.x0 < width / 2 ? "start" : "end"; })
      .text(function (d) { return d.name; })
      .attr("class", "sankey-label");
  }

  function load() {
    fetch(withWeek("/api/sankey", activeWeekKey))
      .then(function (res) {
        if (!res.ok) throw new Error("Could not load data");
        return res.json();
      })
      .then(function (data) {
        latestData = data;
        var week = data && data.week || {};
        activeWeekKey = parseWeekKey(week.key) || activeWeekKey || toWeekKey(new Date());
        currentWeekKey = parseWeekKey(week.currentKey) || currentWeekKey || toWeekKey(new Date());
        updateWeekCopyToggleState(week.copyToNextWeek !== false);
        setWeekInUrl(activeWeekKey);
        refreshWeekUi();
        renderSummary(data);
        renderSankey(data);
        renderUtilizationTable(data);
      })
      .catch(function () {
        if (sankeyWrapEl) sankeyWrapEl.innerHTML = "<p class=\"text-danger\">Failed to load Sankey data.</p>";
      });
  }

  if (!parseWeekKey(activeWeekKey)) activeWeekKey = toWeekKey(new Date());
  refreshWeekUi();

  if (allocationCloseBtn) allocationCloseBtn.addEventListener("click", closeAllocationDrawer);
  if (allocationBackdropEl) allocationBackdropEl.addEventListener("click", closeAllocationDrawer);
  if (allocationRowsEl) {
    allocationRowsEl.addEventListener("click", function (event) {
      var removeBtn = event.target.closest(".allocation-remove");
      if (!removeBtn) return;
      var row = removeBtn.closest(".allocation-row");
      if (row) row.remove();
    });
  }
  if (allocationAddRowBtn) {
    allocationAddRowBtn.addEventListener("click", function () {
      var projects = latestData && latestData.projects ? latestData.projects : [];
      addAllocationRow(projects, "", "");
    });
  }
  if (allocationFormEl) {
    allocationFormEl.addEventListener("submit", function (event) {
      event.preventDefault();
      saveEmployeeAllocations();
    });
  }

  if (weekPrevBtn) {
    weekPrevBtn.addEventListener("click", function () {
      activeWeekKey = shiftWeekKey(activeWeekKey, -1);
      load();
    });
  }

  if (weekNextBtn) {
    weekNextBtn.addEventListener("click", function () {
      activeWeekKey = shiftWeekKey(activeWeekKey, 1);
      load();
    });
  }

  if (weekCopyToggleEl && !readOnly) {
    weekCopyToggleEl.addEventListener("change", function () {
      if (isSavingWeekSettings) return;
      isSavingWeekSettings = true;
      updateWeekCopyToggleState(weekCopyToggleEl.checked);
      fetch(withWeek("/api/week-settings", activeWeekKey), {
        method: "PUT",
        headers: { "Content-Type": "application/json" },
        body: JSON.stringify({ copyToNextWeek: !!weekCopyToggleEl.checked })
      })
        .then(function (res) {
          if (!res.ok) {
            return res.json().then(function (payload) {
              throw new Error(payload && payload.error ? payload.error : "Failed to save week setting");
            });
          }
          return res.json();
        })
        .then(function (payload) {
          updateWeekCopyToggleState(payload && payload.copyToNextWeek !== false);
          load();
        })
        .catch(function (error) {
          window.alert((error && error.message) || "Failed to save week setting.");
          load();
        })
        .finally(function () {
          isSavingWeekSettings = false;
          if (weekCopyToggleEl) weekCopyToggleEl.disabled = false;
        });
    });
  }

  load();
  window.addEventListener("resize", function () {
    window.clearTimeout(window.__sankeyResizeTimer);
    window.__sankeyResizeTimer = window.setTimeout(load, 150);
  });
  document.addEventListener("keydown", function (event) {
    if (event.key === "Escape" && allocationDrawerEl && !allocationDrawerEl.hidden) {
      closeAllocationDrawer();
    }
  });
})();
