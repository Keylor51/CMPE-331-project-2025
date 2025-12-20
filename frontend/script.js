// ====================================================================
// CONFIGURATION AND GLOBALS
// ====================================================================
const API_BASE_URL = 'http://localhost:8080/api'; 
const FLIGHT_LIST_URL = `${API_BASE_URL}/roster/flights`; 

let currentFlightId = null; 
let currentRosterData = null; 
let isRosterValid = false;
let availableFlights = []; 

// Variables to keep track of state
let candidatePilots = [];
let candidateCrew = [];
let selectedPilots = [];
let selectedCrew = [];
let currentMenu = [];

const AIRPORTS = [
    { code: "IST", name: "Istanbul Airport", lat: 41.2811, lon: 28.7519 },
    { code: "LHR", name: "London Heathrow", lat: 51.4700, lon: -0.4543 },
    { code: "JFK", name: "New York JFK", lat: 40.6413, lon: -73.7781 },
    { code: "NRT", name: "Tokyo Narita", lat: 35.7720, lon: 140.3929 },
    { code: "CDG", name: "Paris Charles de Gaulle", lat: 49.0097, lon: 2.5479 },
    { code: "DXB", name: "Dubai International", lat: 25.2532, lon: 55.3657 },
    { code: "FRA", name: "Frankfurt Airport", lat: 50.0379, lon: 8.5622 },
    { code: "LAX", name: "Los Angeles Intl", lat: 33.9416, lon: -118.4085 }
];

const VEHICLE_CONFIGS = {
    "Embraer E195": "{\"sections\": [{\"className\": \"BUSINESS\", \"rows\": 3, \"layout\": [1, 2], \"letters\": \"ADF\"},{\"className\": \"ECONOMY\", \"rows\": 12, \"layout\": [2, 2], \"letters\": \"ACDF\"}]}",
    "Boeing 737-800": "{\"sections\": [{\"className\": \"BUSINESS\", \"rows\": 3, \"layout\": [2, 2], \"letters\": \"ACDF\"},{\"className\": \"ECONOMY\", \"rows\": 25, \"layout\": [3, 3], \"letters\": \"ABCDEF\"}]}",
    "Boeing 787 Dreamliner": "{\"sections\": [{\"className\": \"BUSINESS\", \"rows\": 5, \"layout\": [2, 2, 2], \"letters\": \"ACDGHK\"},{\"className\": \"ECONOMY\", \"rows\": 35, \"layout\": [3, 4, 3], \"letters\": \"ABCDEFGHJK\"}]}"
};

// ====================================================================
// VIEW MANAGEMENT & AUTH
// ====================================================================

function setView(showId) {
    ['loginSection', 'searchSection', 'rosterSection', 'userInfo'].forEach(id => {
        const el = document.getElementById(id);
        if(el) el.classList.add('hidden');
    });
    if (document.getElementById(showId)) document.getElementById(showId).classList.remove('hidden');
    
    const token = localStorage.getItem('jwt_token');
    const username = localStorage.getItem('username');

    if (token) {
        document.getElementById('userInfo').classList.remove('hidden');
        if (showId === 'searchSection' || showId === 'rosterSection') {
            applyPermissions(username);
        }
    }
    
    if (showId === 'searchSection') loadAvailableFlights();
}

function applyPermissions(username) {
    const isAdmin = (username === 'admin');
    const createBtn = document.getElementById('createFlightBtn');
    const saveBtn = document.getElementById('saveRosterBtn');

    if (createBtn) createBtn.style.display = isAdmin ? 'inline-flex' : 'none';
    if (saveBtn) saveBtn.style.display = isAdmin ? 'inline-flex' : 'none';
    
    if (document.getElementById('rosterSection').classList.contains('hidden') === false) {
        renderSelectionTables(); 
    }
}

document.addEventListener('DOMContentLoaded', () => {
    populateAirportSelects();
    if (localStorage.getItem('jwt_token')) {
        try {
            const username = localStorage.getItem('username') || "User";
            document.getElementById('userDisplay').innerText = username;
            setView('searchSection');
        } catch(e) { logout(); }
    } else {
        setView('loginSection');
    }
});

async function handleLogin(e) {
    e.preventDefault();
    const inputs = e.target.querySelectorAll('input');
    const username = inputs[0].value;
    const password = inputs[1].value;
    const authString = btoa(`${username}:${password}`); 
    try {
        const response = await fetch(`${API_BASE_URL}/roster/flights?t=${new Date().getTime()}`, { 
            method: 'GET',
            headers: { 'Authorization': `Basic ${authString}` }
        });
        if (response.ok) {
            localStorage.setItem('jwt_token', authString); 
            localStorage.setItem('username', username);
            document.getElementById('userDisplay').innerText = username;
            setView('searchSection');
        } else alert("Login failed! Check credentials.");
    } catch (error) { alert("Could not connect to MainSystem API. Is Docker running?"); }
}

function logout() {
    localStorage.removeItem('jwt_token');
    localStorage.removeItem('username');
    currentFlightId = null;
    setView('loginSection');
}

// ====================================================================
// Loading data from API
// ====================================================================

async function loadAvailableFlights() {
    const token = localStorage.getItem('jwt_token');
    const tbody = document.getElementById('flightsTableBody');
    document.getElementById('flightListContainer').classList.remove('hidden');
    
    if(availableFlights.length === 0) {
        tbody.innerHTML = '<tr><td colspan="6" class="text-center">Loading flights...</td></tr>';
    }

    try {
        const response = await fetch(FLIGHT_LIST_URL + `?t=${new Date().getTime()}`, { 
            method: 'GET', 
            headers: { 'Authorization': `Basic ${token}` } 
        });
        if (response.ok) {
            const apiFlights = await response.json();
            
            // Removed isLocal logic, just merging lists now
            // If it exists in the list get the backend version, otherwise keep the local one (newly added).
            
            // 1. Taking the new list (Backend) as reference
            const merged = [...apiFlights];

            // 2. Adding flights we have locally but aren't in backend yet (unsaved new flights)
            availableFlights.forEach(localF => {
                const exists = apiFlights.find(apiF => apiF.flightNumber === localF.flightNumber);
                if (!exists) {
                    merged.push(localF);
                }
            });

            availableFlights = merged;
            renderFlightsTable(availableFlights);
        } else {
            if(availableFlights.length > 0) renderFlightsTable(availableFlights);
            else tbody.innerHTML = `<tr><td colspan="6" style="color:var(--danger)">Error loading backend flights: ${response.status}</td></tr>`;
        }
    } catch (error) { 
        if(availableFlights.length > 0) renderFlightsTable(availableFlights);
        else tbody.innerHTML = `<tr><td colspan="6" style="color:var(--danger)">Connection Error</td></tr>`; 
    }
}

function renderFlightsTable(flights) {
    const tbody = document.getElementById('flightsTableBody');
    tbody.innerHTML = '';
    if (flights.length === 0) {
        tbody.innerHTML = '<tr><td colspan="6" class="text-center">No flights found.</td></tr>';
        return;
    }
    flights.forEach(f => {
        const sCode = f.source.code || f.source;
        const dCode = f.destination.code || f.destination;
        
        tbody.innerHTML += `<tr>
                <td><b>${f.flightNumber}</b></td>
                <td>${sCode}</td>
                <td>${dCode}</td>
                <td>${new Date(f.dateTime).toLocaleString()}</td>
                <td>${f.distanceKm} km</td>
                <td class="text-right"><button onclick="selectFlight('${f.flightNumber}')" class="btn btn-sm btn-outline">Select <i class="fas fa-chevron-right"></i></button></td>
            </tr>`;
    });
}

function filterFlights() {
    const term = document.getElementById('flightIdInput').value.trim().toUpperCase();
    const filtered = availableFlights.filter(f => f.flightNumber.toUpperCase().includes(term));
    renderFlightsTable(filtered);
}

function selectFlight(flightId) {
    document.getElementById('flightIdInput').value = flightId;
    filterFlights();
    currentFlightId = flightId;
    loadRoster(flightId);
}

function goBack() {
    currentFlightId = null;
    currentRosterData = null;
    selectedPilots = [];
    selectedCrew = [];
    currentMenu = [];
    setView('searchSection');
}

// --- MAIN CHANGE: No isLocal check, ask Backend directly ---
async function loadRoster(flightId) {
    const token = localStorage.getItem('jwt_token');
    
    // First find flight info in memory (might need vehicle type etc.)
    let targetFlight = availableFlights.find(f => f.flightNumber === flightId);
    
    console.log("Attempting to load roster for: " + flightId);

    try {
        // Ask backend: Is there a saved roster for this flight?
        const response = await fetch(`${API_BASE_URL}/roster/generate/${flightId}?t=${new Date().getTime()}`, { 
            method: 'GET', 
            headers: { 'Authorization': `Basic ${token}` } 
        });
        
        if (response.ok) {
            // FOUND IN BACKEND! (Full Roster)
            console.log("Roster found in Backend!");
            currentRosterData = await response.json();
            
            // Load the data
            selectedPilots = currentRosterData.pilots ? [...currentRosterData.pilots] : [];
            selectedCrew = currentRosterData.cabinCrew ? [...currentRosterData.cabinCrew] : [];
            currentMenu = currentRosterData.menu || [];

        } else {
            // NOT IN BACKEND OR ERROR (404 etc.)
            // So this flight isn't saved yet (New/Draft)
            console.log("Roster not found in Backend. Creating empty draft.");
            
            if (!targetFlight) {
                alert("Critical Error: Flight details not found in local cache.");
                return;
            }

            // Create empty template
            currentRosterData = {
                flightInfo: targetFlight,
                pilots: [],
                cabinCrew: [],
                passengers: [], 
                menu: ["Standard Menu (Pending Chef Selection)"],
                flightId: flightId,
                generatedDate: new Date()
            };
            selectedPilots = [];
            selectedCrew = [];
            currentMenu = currentRosterData.menu;
        }

        // Common operations (fetch candidates, update UI)
        const vModel = currentRosterData.flightInfo.vehicleType ? currentRosterData.flightInfo.vehicleType.modelName : "Unknown";
        await fetchCandidates(vModel, token, currentRosterData.flightInfo.dateTime);
        
        updateUI();
        
        let titleText = `${flightId} - Roster Details`;
        if (response.ok) titleText += " (Saved)";
        else titleText += " (Draft)";
        
        safeSetText('selectedFlightTitle', titleText);
        switchView('tabular'); 
        setView('rosterSection');

    } catch (error) { 
        console.error(error);
        alert("Connection Error. Could not verify roster status."); 
    }
}

function safeSetText(id, text) {
    const el = document.getElementById(id);
    if(el) el.innerText = text;
}

async function fetchCandidates(vehicleType, token, dateTime) {
    try {
        let params = "";
        if (dateTime) params += `?date=${dateTime}`;
        if (currentFlightId) params += `&currentFlightId=${currentFlightId}`;
        params += `&t=${new Date().getTime()}`;

        const url = `${API_BASE_URL}/roster/candidates/pilots/${encodeURIComponent(vehicleType)}${params}`;
        const resP = await fetch(url, { headers: { 'Authorization': `Basic ${token}` } });
        if(resP.ok) candidatePilots = await resP.json();
        
        const urlCrew = `${API_BASE_URL}/roster/candidates/crew${params}`;
        const resC = await fetch(urlCrew, { headers: { 'Authorization': `Basic ${token}` } });
        if(resC.ok) candidateCrew = await resC.json();
    } catch (e) { console.error("Error fetching candidates:", e); }
}

// ... (Create Flight Functions are same) ...
function openCreateFlightModal() {
    document.getElementById('createFlightForm').reset();
    document.getElementById('createFlightModal').style.display = 'block';
}
function closeModal(id) { document.getElementById(id).style.display = 'none'; }

function populateAirportSelects() {
    const sSelect = document.getElementById('sourceSelect');
    const dSelect = document.getElementById('destSelect');
    if(!sSelect || !dSelect) return;
    if(sSelect.options.length > 1) return;
    AIRPORTS.forEach(a => {
        const opt = `<option value="${a.code}" data-lat="${a.lat}" data-lon="${a.lon}">${a.code} - ${a.name}</option>`;
        sSelect.innerHTML += opt;
        dSelect.innerHTML += opt;
    });
}

function calculateDistance() {
    const sSelect = document.getElementById('sourceSelect');
    const dSelect = document.getElementById('destSelect');
    if(!sSelect || !dSelect) return;
    const sOpt = sSelect.options[sSelect.selectedIndex];
    const dOpt = dSelect.options[dSelect.selectedIndex];
    if (sOpt.value && dOpt.value && sOpt.value !== dOpt.value) {
        const lat1 = parseFloat(sOpt.getAttribute('data-lat'));
        const lon1 = parseFloat(sOpt.getAttribute('data-lon'));
        const lat2 = parseFloat(dOpt.getAttribute('data-lat'));
        const lon2 = parseFloat(dOpt.getAttribute('data-lon'));
        const R = 6371; 
        const dLat = (lat2 - lat1) * Math.PI / 180;
        const dLon = (lon2 - lon1) * Math.PI / 180;
        const a = Math.sin(dLat/2) * Math.sin(dLat/2) + Math.cos(lat1 * Math.PI / 180) * Math.cos(lat2 * Math.PI / 180) * Math.sin(dLon/2) * Math.sin(dLon/2);
        const c = 2 * Math.atan2(Math.sqrt(a), Math.sqrt(1-a));
        document.getElementById('distanceInput').value = Math.round(R * c);
    } else { document.getElementById('distanceInput').value = ""; }
}

function handleCreateFlight(e) {
    e.preventDefault();
    const formData = new FormData(e.target);
    const rawNo = formData.get('flightNumber').toUpperCase();
    const flightNo = rawNo.replace(/\s+/g, ''); 
    const vehicleName = formData.get('vehicleType');
    const distVal = formData.get('distance');
    if(!flightNo || !vehicleName || !distVal) { alert("Please fill all fields"); return; }
    
    const newFlight = {
        flightNumber: flightNo,
        source: { code: formData.get('source') }, 
        destination: { code: formData.get('destination') },
        dateTime: formData.get('dateTime'),
        distanceKm: parseInt(distVal),
        vehicleType: {
            modelName: vehicleName,
            seatingPlanConfig: VEHICLE_CONFIGS[vehicleName],
            standardMenuDescription: "Standard Menu"
        }
        // NO isLocal - Adding directly, if not in Backend loadRoster gets 404 and creates Draft
    };
    availableFlights.push(newFlight);
    renderFlightsTable(availableFlights);
    closeModal('createFlightModal');
    selectFlight(flightNo);
}

// ... (UI Update Functions are same) ...
function updateUI() {
    if(!currentRosterData) return;
    renderTabularView();
    renderPlaneView();
    renderExtendedView();
    renderSelectionTables();
    renderMenu();
    renderCrewManifest(); 
    validateRules();
}

function validateRules() {
    isRosterValid = true;
    let msg = "";
    const pSeniors = selectedPilots.filter(p => p.seniorityLevel === 'SENIOR').length;
    const pJuniors = selectedPilots.filter(p => p.seniorityLevel === 'JUNIOR').length;
    if (selectedPilots.length !== 2) { msg += "<div><i class='fas fa-times'></i> Must have exactly 2 Pilots.</div>"; isRosterValid = false; } 
    else if (pSeniors < 1 || pJuniors < 1) { msg += "<div><i class='fas fa-times'></i> Pilots: Need 1 Senior & 1 Junior.</div>"; isRosterValid = false; } 
    else { msg += "<div style='color:var(--success)'><i class='fas fa-check'></i> Pilots: OK (1 Sen, 1 Jun)</div>"; }
    const cSeniors = selectedCrew.filter(c => c.seniority === 'SENIOR' && c.type !== 'CHEF').length;
    const cJuniors = selectedCrew.filter(c => c.seniority === 'JUNIOR' && c.type !== 'CHEF').length;
    const cChefs = selectedCrew.filter(c => c.type === 'CHEF').length;
    if (cSeniors < 1 || cSeniors > 4) { msg += `<div><i class='fas fa-times'></i> Crew Seniors: ${cSeniors} (Need 1-4)</div>`; isRosterValid = false; }
    else msg += `<div style='color:var(--success)'><i class='fas fa-check'></i> Crew Seniors: OK</div>`;
    if (cJuniors < 4 || cJuniors > 16) { msg += `<div><i class='fas fa-times'></i> Crew Juniors: ${cJuniors} (Need 4-16)</div>`; isRosterValid = false; }
    else msg += `<div style='color:var(--success)'><i class='fas fa-check'></i> Crew Juniors: OK</div>`;
    if (cChefs > 2) { msg += `<div><i class='fas fa-times'></i> Chefs: ${cChefs} (Max 2)</div>`; isRosterValid = false; }
    const div = document.getElementById('crewValidationMsg');
    if(div) { div.innerHTML = msg; div.style.borderLeft = isRosterValid ? "4px solid var(--success)" : "4px solid var(--danger)"; }
}

function renderPlaneView() {
    const cockpit = document.getElementById('cockpitGrid'); 
    if(cockpit) {
        cockpit.innerHTML = '';
        selectedPilots.forEach(p => cockpit.appendChild(createSeatElement(p, 'pilot', 'PLT')));
    }
    const crewDiv = document.getElementById('cabinCrewGrid'); 
    if(crewDiv) {
        crewDiv.innerHTML = '';
        selectedCrew.forEach(c => crewDiv.appendChild(createSeatElement(c, 'crew', 'CRW')));
    }
    const grid = document.getElementById('passengerGrid'); 
    if(!grid) return;
    grid.innerHTML = '';
    let config = { sections: [] };
    if(currentRosterData.flightInfo && currentRosterData.flightInfo.vehicleType) {
        try { 
            const configData = currentRosterData.flightInfo.vehicleType.seatingPlanConfig;
            if(typeof configData === 'string') { config = JSON.parse(configData); } 
            else if (typeof configData === 'object') { config = configData; }
        } catch(e) { console.error("Invalid Seating Plan JSON", e); }
    }
    const pMap = {};
    (currentRosterData.passengers||[]).forEach(p => { if(p.seatNumber) pMap[p.seatNumber.trim()] = p; });
    let currentRowNum = 1;
    if(config.sections) {
        config.sections.forEach(section => {
            const sectionClass = (section.className && section.className.toUpperCase() === 'BUSINESS') ? 'business' : 'economy';
            for (let r = 0; r < section.rows; r++) {
                const rowDiv = document.createElement('div'); rowDiv.className = 'seat-row';
                const rowNum = document.createElement('div'); rowNum.className = 'aisle-spacer'; rowNum.innerText = currentRowNum;
                rowDiv.appendChild(rowNum);
                let letterIdx = 0;
                section.layout.forEach((groupSize, gIdx) => {
                    const group = document.createElement('div'); group.className = 'seat-group';
                    for(let s=0; s<groupSize; s++) {
                        const l = section.letters[letterIdx] || '?';
                        const id = `${currentRowNum}${l}`;
                        const p = pMap[id];
                        if(p) group.appendChild(createSeatElement(p, `${sectionClass} occupied`, id));
                        else {
                            const empty = document.createElement('div');
                            empty.className = `seat ${sectionClass}`; empty.innerText = l;
                            empty.onmouseenter = (e) => showTooltip(e, {name: 'Empty', seatNumber: id, seatType: sectionClass});
                            empty.onmouseleave = hideTooltip;
                            group.appendChild(empty);
                        }
                        letterIdx++;
                    }
                    rowDiv.appendChild(group);
                    if(gIdx < section.layout.length - 1) {
                        const aisle = document.createElement('div'); aisle.className='aisle-spacer'; aisle.style.width='15px';
                        rowDiv.appendChild(aisle);
                    }
                });
                grid.appendChild(rowDiv);
                currentRowNum++;
            }
        });
    }
}

function createSeatElement(person, cls, lbl) {
    const d = document.createElement('div'); d.className = `seat ${cls}`; d.innerText = lbl||'';
    d.onmouseenter = (e) => showTooltip(e, {...person, type: cls.split(' ')[0]});
    d.onmouseleave = hideTooltip;
    return d;
}

function showTooltip(e, data) {
    const t = document.getElementById('seatTooltip');
    if(!t) return;
    let c = `<div style="font-weight:600; margin-bottom:4px;">${data.name}</div>`;
    if(data.seatNumber) c+=`<div style="font-size:0.8em; opacity:0.8">Seat: ${data.seatNumber}</div>`;
    if(data.type) c+=`<div style="font-size:0.8em; opacity:0.8">Type: ${data.type}</div>`;
    t.innerHTML = c;
    t.style.left = (e.clientX + 15) + 'px'; t.style.top = (e.clientY + 15) + 'px';
    t.classList.remove('hidden');
}
function hideTooltip() { 
    const t = document.getElementById('seatTooltip');
    if(t) t.classList.add('hidden'); 
}

function addCrew(id) {
    const c = candidateCrew.find(x => x.id === id);
    if (c) {
        let currentVehicle = "Unknown";
        if(currentRosterData && currentRosterData.flightInfo && currentRosterData.flightInfo.vehicleType) {
            currentVehicle = currentRosterData.flightInfo.vehicleType.modelName;
        }
        const allowedList = c.allowedVehicles || [];
        const isCompatible = allowedList.some(v => 
            currentVehicle.toLowerCase().includes(v.toLowerCase()) || 
            v.toLowerCase().includes(currentVehicle.toLowerCase())
        );
        if (!isCompatible) {
            alert(`⚠️ Cannot Add Crew Member!\n\n${c.name} is not certified for ${currentVehicle}.\nCertified for: ${allowedList.join(", ")}`);
            return;
        }
        selectedCrew.push(c);
        updateUI();
    }
}

function renderSelectionTables() {
    const username = localStorage.getItem('username');
    const isAdmin = (username === 'admin');
    const crewBody = document.getElementById('selectedCrewTable').querySelector('tbody');
    crewBody.innerHTML = '';
    selectedCrew.forEach(c => {
        let removeBtn = isAdmin ? `<button onclick="removeCrew(${c.id})" class="btn btn-sm btn-danger"><i class="fas fa-minus"></i></button>` : `<span class="text-muted" style="font-size:0.8em; color:#999;">Read Only</span>`;
        crewBody.innerHTML += `<tr><td>${c.name}</td><td>${c.seniority}</td><td class="text-right">${removeBtn}</td></tr>`;
    });
    const candCrewBody = document.getElementById('candidateCrewTable').querySelector('tbody');
    candCrewBody.innerHTML = '';
    let currentVehicle = "";
    if(currentRosterData.flightInfo && currentRosterData.flightInfo.vehicleType) {
        currentVehicle = currentRosterData.flightInfo.vehicleType.modelName;
    }
    candidateCrew.forEach(c => {
        if (!selectedCrew.find(sc => sc.id === c.id)) {
            let badge = '';
            if(c.type === 'CHIEF') badge = '<span class="badge badge-primary" style="margin-left:5px; font-size:0.7em">CHIEF</span>';
            else if(c.type === 'CHEF') badge = '<span class="badge badge-warning" style="margin-left:5px; font-size:0.7em">CHEF</span>';
            else badge = '<span class="badge badge-light" style="margin-left:5px; font-size:0.7em">REGULAR</span>';
            let actionBtn = '';
            if (isAdmin) {
                const isCompatible = (c.allowedVehicles || []).some(v => 
                    currentVehicle.toLowerCase().includes(v.toLowerCase()) || 
                    v.toLowerCase().includes(currentVehicle.toLowerCase())
                );
                if (isCompatible) {
                    actionBtn = `<button onclick="addCrew(${c.id})" class="btn btn-sm btn-success"><i class="fas fa-plus"></i></button>`;
                } else {
                    actionBtn = `<button class="btn btn-sm btn-ghost" disabled style="cursor:not-allowed; opacity:0.5" title="Not Certified for ${currentVehicle}"><i class="fas fa-ban" style="color:red"></i></button>`;
                }
            } else { actionBtn = `<span class="text-muted" style="font-size:0.8em; color:#999;">-</span>`; }
            candCrewBody.innerHTML += `<tr><td><div style="font-weight:600">${c.name}</div>${badge}</td><td>${c.seniority}</td><td class="text-right">${actionBtn}</td></tr>`;
        }
    });
    renderPilotTables();
}

function renderPilotTables() {
    const username = localStorage.getItem('username');
    const isAdmin = (username === 'admin');
    const pilotBody = document.getElementById('selectedPilotTable').querySelector('tbody');
    pilotBody.innerHTML = '';
    selectedPilots.forEach(p => {
        let removeBtn = isAdmin ? `<button onclick="removePilot(${p.id})" class="btn btn-sm btn-danger"><i class="fas fa-minus"></i></button>` : `<span class="text-muted" style="font-size:0.8em; color:#999;">Read Only</span>`;
        pilotBody.innerHTML += `<tr><td>${p.name}</td><td><span class="badge">${p.seniorityLevel}</span></td><td class="text-right">${removeBtn}</td></tr>`;
    });
    const candPilotBody = document.getElementById('candidatePilotTable').querySelector('tbody');
    candPilotBody.innerHTML = '';
    candidatePilots.forEach(p => {
        if (!selectedPilots.find(sp => sp.id === p.id)) {
            let addBtn = isAdmin ? `<button onclick="addPilot(${p.id})" class="btn btn-sm btn-success"><i class="fas fa-plus"></i></button>` : `<span class="text-muted" style="font-size:0.8em; color:#999;">-</span>`;
            candPilotBody.innerHTML += `<tr><td>${p.name}</td><td>${p.seniorityLevel}</td><td class="text-right">${addBtn}</td></tr>`;
        }
    });
}

function addPilot(id) { const p = candidatePilots.find(x => x.id === id); if(p) { selectedPilots.push(p); updateUI(); } }
function removePilot(id) { selectedPilots = selectedPilots.filter(x => x.id !== id); updateUI(); }
function removeCrew(id) { selectedCrew = selectedCrew.filter(x => x.id !== id); updateUI(); }

function renderMenu() {
    const div = document.getElementById('flightMenuDisplay');
    if(!div) return;
    if (currentMenu.length > 0) {
        div.classList.remove('hidden');
        document.getElementById('menuItems').innerText = currentMenu.join(' + ');
    } else div.classList.add('hidden');
}

function renderTabularView() {
    const tbody = document.getElementById('summaryTableBody');
    if(!tbody) return;
    tbody.innerHTML = '';
    const all = [...selectedPilots.map(p => ({...p, type: 'Pilot'})), ...selectedCrew.map(c => ({...c, type: 'Cabin Crew'})), ...(currentRosterData.passengers||[]).map(p => ({...p, type: 'Passenger'}))];
    all.forEach(p => {
        tbody.innerHTML += `<tr><td><span class="badge badge-light">${p.type}</span></td><td>${p.id}</td><td><strong>${p.name}</strong></td><td>${p.gender||'-'}</td><td>${p.nationality||'-'}</td></tr>`;
    });
}

function renderCrewManifest() {
    const tbody = document.getElementById('finalCrewTableBody');
    if(!tbody) return;
    tbody.innerHTML = '';
    const pilots = selectedPilots.map(p => ({...p, role: 'Pilot', badgeClass: 'badge-primary', rank: p.seniorityLevel, info: (p.languages || []).join(', ')}));
    const crew = selectedCrew.map(c => ({...c, role: 'Cabin Crew', badgeClass: 'badge-warning', rank: c.seniority, info: (c.languages || []).join(', ')}));
    const passengers = (currentRosterData.passengers || []).map(p => ({...p, role: 'Passenger', badgeClass: 'badge-light', rank: p.seatNumber || 'Unassigned', info: p.seatType}));
    const fullManifest = [...pilots, ...crew, ...passengers];
    if (fullManifest.length === 0) {
        tbody.innerHTML = '<tr><td colspan="6" class="text-center" style="padding:20px; color:#999;">No manifest data available.</td></tr>';
        return;
    }
    fullManifest.forEach(p => {
        tbody.innerHTML += `<tr><td><span class="badge ${p.badgeClass}">${p.role}</span></td><td>${p.id}</td><td><strong>${p.name}</strong></td><td>${p.rank || '-'}</td><td>${p.nationality||'-'}</td><td><small>${p.info || '-'}</small></td></tr>`;
    });
}

function renderExtendedView() {
    const pilotTable = document.getElementById('pilotTable');
    if(pilotTable) {
        pilotTable.querySelector('thead').innerHTML = `<tr><th>ID</th><th>Name</th><th>Age</th><th>Gender</th><th>Nat.</th><th>Range (km)</th><th>Vehicle</th><th>Level</th><th>Languages</th></tr>`;
        populateTable(pilotTable, selectedPilots, p => `<td>${p.id}</td><td><strong>${p.name}</strong></td><td>${p.age}</td><td>${p.gender}</td><td>${p.nationality}</td><td>${p.allowedRangeKm}</td><td><small>${p.allowedVehicleType}</small></td><td>${p.seniorityLevel}</td><td><small>${(p.languages || []).join(', ')}</small></td>`);
    }
    const crewTable = document.getElementById('crewTable');
    if(crewTable) {
        crewTable.querySelector('thead').innerHTML = `<tr><th>ID</th><th>Name</th><th>Age</th><th>Gender</th><th>Nat.</th><th>Type</th><th>Seniority</th><th>Langs</th><th>Vehicles</th><th>Recipes</th></tr>`;
        populateTable(crewTable, selectedCrew, c => `<td>${c.id}</td><td><strong>${c.name}</strong></td><td>${c.age}</td><td>${c.gender}</td><td>${c.nationality}</td><td>${c.type}</td><td>${c.seniority}</td><td><small>${(c.languages || []).join(', ')}</small></td><td><small>${(c.allowedVehicles || []).join(', ')}</small></td><td><small><i>${(c.chefRecipes || []).join(', ')}</i></small></td>`);
    }
    const passTable = document.getElementById('passengerTable');
    if(passTable) {
        passTable.querySelector('thead').innerHTML = `<tr><th>ID</th><th>Name</th><th>Age</th><th>Gender</th><th>Nat.</th><th>Seat Class</th><th>Seat No</th><th>Parent ID</th><th>Affiliated IDs</th></tr>`;
        populateTable(passTable, currentRosterData.passengers||[], p => `<td>${p.id}</td><td><strong>${p.name}</strong></td><td>${p.age}</td><td>${p.gender}</td><td>${p.nationality}</td><td><span class="badge ${p.seatType === 'BUSINESS' ? 'badge-primary' : 'badge-light'}">${p.seatType}</span></td><td>${p.seatNumber || '-'}</td><td>${p.parentId || '-'}</td><td><small>${(p.affiliatedPassengerIds || []).join(', ')}</small></td>`);
    }
}

function populateTable(tbl, data, fmt) {
    const tb = tbl.querySelector('tbody'); tb.innerHTML='';
    if(!data||data.length===0) tb.innerHTML='<tr><td colspan="10" class="text-center">No data available</td></tr>';
    else data.forEach(i => { const tr=document.createElement('tr'); tr.innerHTML=fmt(i); tb.appendChild(tr); });
}

function switchView(v) {
    ['tabularView','planeView','extendedView'].forEach(id => {
        const el = document.getElementById(id);
        if(el) el.classList.add('hidden');
    });
    const target = document.getElementById(v+'View');
    if(target) target.classList.remove('hidden');
    document.querySelectorAll('.tab-btn').forEach(b=>b.classList.remove('active'));
    const activeBtn = document.querySelector(`.tab-btn[onclick*="${v}"]`);
    if(activeBtn) activeBtn.classList.add('active');
}

function openSaveModal() { 
    if(!isRosterValid) {
        alert("Cannot Save: Roster requirements are not met!\nPlease check the warnings in red.");
        return;
    }
    document.getElementById('saveModal').style.display='block'; 
}
function closeModal(id) { document.getElementById(id).style.display='none'; }

async function saveToDB(type) {
    const token = localStorage.getItem('jwt_token');
    
    // Make sure data is safe
    currentRosterData.pilots = selectedPilots;
    currentRosterData.cabinCrew = selectedCrew;

    try {
        const response = await fetch(`${API_BASE_URL}/roster/save?dbType=${type}`, {
            method: 'POST',
            headers: { 'Authorization': `Basic ${token}`, 'Content-Type': 'application/json' },
            body: JSON.stringify(currentRosterData)
        });
        if(response.ok) { 
            alert("Roster Saved Successfully!"); 
            closeModal('saveModal'); 
            
            // REFRESH LIST (Pull new data from Backend)
            await loadAvailableFlights(); 
        } 
        else { const msg = await response.text(); alert("Save Failed:\n" + msg); }
    } catch(e) { alert("Save Error"); }
}

function exportJson() {
    if(!currentRosterData) {
        alert("No roster data to export!");
        return;
    }

    try {
        const jsonStr = JSON.stringify(currentRosterData, null, 2);
        const blob = new Blob([jsonStr], { type: 'application/json' });
        const url = URL.createObjectURL(blob);
        const a = document.createElement('a'); 
        a.href = url; 
        a.download = `roster_${currentFlightId || 'data'}.json`;
        document.body.appendChild(a); 
        a.click(); 
        document.body.removeChild(a);
        URL.revokeObjectURL(url);
    } catch(e) {
        console.error("Export failed", e);
        alert("Failed to export JSON file. See console for details.");
    }
}