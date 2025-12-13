// ====================================================================
// CONFIGURATION AND GLOBALS
// ====================================================================
const API_BASE_URL = 'http://localhost:8080/api'; 
const FLIGHT_LIST_URL = `${API_BASE_URL}/roster/flights`; 

let currentFlightId = null; 
let currentRosterData = null; 

// STATE VARIABLES FOR MANUAL SELECTION
let candidatePilots = [];
let candidateCrew = [];
let selectedPilots = [];
let selectedCrew = [];
let currentMenu = ["Standard Menu"];

// --- 1. VIEW MANAGEMENT ---
function setView(showId) {
    ['loginSection', 'searchSection', 'rosterSection', 'userInfo'].forEach(id => {
        const el = document.getElementById(id);
        if(el) el.classList.add('hidden');
    });
    
    if (document.getElementById(showId)) {
        document.getElementById(showId).classList.remove('hidden');
    }
    
    if (localStorage.getItem('jwt_token')) {
        const userInfo = document.getElementById('userInfo');
        if(userInfo) userInfo.classList.remove('hidden');
    }

    if (showId === 'searchSection') {
        loadAvailableFlights();
    }
}

document.addEventListener('DOMContentLoaded', () => {
    if (localStorage.getItem('jwt_token')) {
        const token = localStorage.getItem('jwt_token');
        try {
            const decoded = atob(token);
            const username = decoded.split(':')[0];
            document.getElementById('userDisplay').innerText = username;
            setView('searchSection');
        } catch(e) {
            logout();
        }
    } else {
        setView('loginSection');
    }
});

async function handleLogin(e) {
    e.preventDefault();
    const username = e.target[0].value;
    const password = e.target[1].value;

    // FIX: Use a valid endpoint to test credentials instead of triggering an error
    const testUrl = `${API_BASE_URL}/roster/flights`; 

    try {
        const authString = btoa(`${username}:${password}`); 
        const response = await fetch(testUrl, { 
            method: 'GET',
            headers: { 'Authorization': `Basic ${authString}` }
        });

        if (response.ok) {
            // Success: Credentials work
            const dummyToken = btoa(username + ':' + password + ':' + Date.now()); 
            localStorage.setItem('jwt_token', dummyToken); 
            document.getElementById('userDisplay').innerText = username;
            setView('searchSection');
        } else if (response.status === 401) {
            alert("Login failed! Invalid username or password.");
        } else {
            // If backend is running but returns error (e.g. 500), show it
            const msg = await response.text();
            alert(`Server Error (${response.status}): ${msg}`);
        }
    } catch (error) {
        alert("Could not connect to MainSystem API on localhost:8080. Is Docker running?"); 
        console.error("Login Network Error:", error);
    }
}

function logout() {
    localStorage.removeItem('jwt_token');
    currentFlightId = null;
    currentRosterData = null;
    setView('loginSection');
}

// --- 2. DATA LOADING & SEARCH ---
async function loadAvailableFlights() {
    const token = localStorage.getItem('jwt_token');
    if (!token) return;
    
    const [username, password] = atob(token).split(':').slice(0, 2);
    const authString = btoa(`${username}:${password}`);
    
    const tbody = document.getElementById('flightsTableBody');
    document.getElementById('flightListContainer').classList.remove('hidden');
    tbody.innerHTML = '<tr><td colspan="5">Loading flights via Main System...</td></tr>';

    try {
        const response = await fetch(FLIGHT_LIST_URL, { method: 'GET', headers: { 'Authorization': `Basic ${authString}` } });
        if (response.ok) {
            const flights = await response.json();
            tbody.innerHTML = '';
            if (flights.length === 0) { tbody.innerHTML = '<tr><td colspan="5">No flights found.</td></tr>'; return; }
            flights.forEach(f => {
                tbody.innerHTML += `<tr>
                        <td><b>${f.flightNumber}</b></td>
                        <td>${f.source.code} (${f.source.city})</td>
                        <td>${f.destination.code} (${f.destination.city})</td>
                        <td>${new Date(f.dateTime).toLocaleString()}</td>
                        <td><button onclick="selectFlight('${f.flightNumber}')" class="btn btn-sm btn-primary">Select <i class="fas fa-chevron-right"></i></button></td>
                    </tr>`;
            });
        } else { tbody.innerHTML = `<tr><td colspan="5" style="color:red">Error: ${response.status}</td></tr>`; }
    } catch (error) { tbody.innerHTML = `<tr><td colspan="5" style="color:red">Connection Error</td></tr>`; }
}

function filterFlightsTable() {
    const input = document.getElementById("flightIdInput");
    const filter = input.value.toUpperCase();
    const tr = document.getElementById("availableFlightsTable").getElementsByTagName("tr");
    for (let i = 1; i < tr.length; i++) {
        const tdText = tr[i].innerText;
        if (tdText.toUpperCase().indexOf(filter) > -1) tr[i].style.display = ""; else tr[i].style.display = "none";
    }
}

function selectFlight(flightId) {
    document.getElementById('flightIdInput').value = flightId;
    filterFlightsTable();
    searchFlights();
}

function goBack() { 
    document.getElementById('flightIdInput').value = '';
    filterFlightsTable();
    setView('searchSection'); 
}

async function searchFlights() {
    const flightId = document.getElementById('flightIdInput').value.trim().toUpperCase();
    if (!flightId) { alert("Please enter a Flight ID."); return; }
    currentFlightId = flightId;
    await loadRoster(flightId);
}

async function loadRoster(flightId) {
    const token = localStorage.getItem('jwt_token');
    const [username, password] = atob(token).split(':').slice(0, 2);
    const authString = btoa(`${username}:${password}`);
    const url = `${API_BASE_URL}/roster/generate/${flightId}`;

    try {
        const response = await fetch(url, { method: 'GET', headers: { 'Authorization': `Basic ${authString}` } });
        if (response.ok) {
            currentRosterData = await response.json();
            
            // INITIALIZE STATE
            selectedPilots = [...currentRosterData.pilots];
            selectedCrew = [...currentRosterData.cabinCrew];
            currentMenu = ["Standard Menu"];
            selectedCrew.forEach(c => {
                if (c.type === 'CHEF') {
                    // Normalize recipe list names
                    const recipes = c.chefRecipes || c.recipes || [];
                    if(recipes.length > 0) {
                        const randomDish = recipes[Math.floor(Math.random() * recipes.length)];
                        c.selectedDish = randomDish;
                        currentMenu.push(randomDish);
                    }
                }
            });

            await fetchCandidates(currentRosterData.flightInfo.vehicleType.modelName, authString);
            updateUI(); 
            
            document.getElementById('selectedFlightTitle').innerText = `${flightId} - Roster Details`;
            switchView('tabular'); 
            setView('rosterSection');
        } else { 
            const msg = await response.text();
            alert(`Failed to load roster (${response.status}): ${msg}`); 
        }
    } catch (error) { alert("Connection Error"); }
}

async function fetchCandidates(vehicleType, authString) {
    try {
        const resP = await fetch(`${API_BASE_URL}/roster/candidates/pilots/${vehicleType}`, { headers: { 'Authorization': `Basic ${authString}` } });
        if(resP.ok) candidatePilots = await resP.json();
        const resC = await fetch(`${API_BASE_URL}/roster/candidates/crew`, { headers: { 'Authorization': `Basic ${authString}` } });
        if(resC.ok) candidateCrew = await resC.json();
    } catch (e) { console.error("Error fetching candidates:", e); }
}

// --- 3. UI RENDERING & LOGIC ---
function updateUI() {
    renderTabularView();
    renderPlaneView();
    renderExtendedView();
    renderSelectionTables();
    renderMenu();
    validateCrewRules();
}

function renderSelectionTables() {
    const cpBody = document.getElementById('candidatePilotTable').querySelector('tbody');
    cpBody.innerHTML = '';
    candidatePilots.forEach(p => {
        if (!selectedPilots.find(sp => sp.id === p.id)) {
            cpBody.innerHTML += `<tr><td>${p.name}</td><td>${p.seniorityLevel}</td><td><button onclick="selectPilot(${p.id})" class="btn btn-sm btn-success">+</button></td></tr>`;
        }
    });

    const ccBody = document.getElementById('candidateCrewTable').querySelector('tbody');
    ccBody.innerHTML = '';
    candidateCrew.forEach(c => {
        if (!selectedCrew.find(sc => sc.id === c.id)) {
            // FIX: Check multiple properties for languages
            const langs = c.languages || c.knownLanguages || [];
            const langStr = langs.length > 0 ? langs.join(', ') : '-';
            
            ccBody.innerHTML += `<tr>
                <td>${c.name} <br><small style="color:#666">${langStr}</small></td>
                <td><span class="badge ${c.type}">${c.type}</span></td>
                <td>${c.seniority}</td>
                <td><button onclick="selectCrew(${c.id})" class="btn btn-sm btn-success">+</button></td>
            </tr>`;
        }
    });

    const spBody = document.getElementById('selectedPilotTable').querySelector('tbody');
    spBody.innerHTML = '';
    selectedPilots.forEach(p => {
        spBody.innerHTML += `<tr><td>${p.name}</td><td>${p.seniorityLevel}</td><td><button onclick="removePilot(${p.id})" class="btn btn-sm btn-danger">-</button></td></tr>`;
    });

    const scBody = document.getElementById('selectedCrewTable').querySelector('tbody');
    scBody.innerHTML = '';
    selectedCrew.forEach(c => {
        let dishInfo = c.selectedDish ? `<i class="fas fa-utensils"></i> ${c.selectedDish}` : '-';
        scBody.innerHTML += `<tr><td>${c.name}</td><td>${c.type}</td><td style="font-size:0.8em; color:#d9534f">${dishInfo}</td><td><button onclick="removeCrew(${c.id})" class="btn btn-sm btn-danger">-</button></td></tr>`;
    });
}

function selectPilot(id) {
    const p = candidatePilots.find(x => x.id === id);
    if(p) { selectedPilots.push(p); currentRosterData.pilots = selectedPilots; updateUI(); }
}
function removePilot(id) {
    selectedPilots = selectedPilots.filter(x => x.id !== id); currentRosterData.pilots = selectedPilots; updateUI();
}
function selectCrew(id) {
    const c = candidateCrew.find(x => x.id === id);
    if(c) {
        if (c.type === 'CHEF') {
            if (selectedCrew.filter(x => x.type === 'CHEF').length >= 2) { alert("Max 2 Chefs allowed!"); return; }
            const recipes = c.chefRecipes || c.recipes || [];
            if(recipes.length > 0) {
                const dish = recipes[Math.floor(Math.random() * recipes.length)];
                c.selectedDish = dish;
                currentMenu.push(dish);
            }
        }
        selectedCrew.push(c); currentRosterData.cabinCrew = selectedCrew; updateUI();
    }
}
function removeCrew(id) {
    const c = selectedCrew.find(x => x.id === id);
    if(c) {
        if (c.type === 'CHEF' && c.selectedDish) currentMenu = currentMenu.filter(m => m !== c.selectedDish);
        selectedCrew = selectedCrew.filter(x => x.id !== id); currentRosterData.cabinCrew = selectedCrew; updateUI();
    }
}

function renderMenu() {
    const div = document.getElementById('flightMenuDisplay');
    if (currentMenu.length > 1) {
        div.classList.remove('hidden');
        document.getElementById('menuItems').innerText = currentMenu.join(' + ');
    } else div.classList.add('hidden');
}

function validateCrewRules() {
    const senior = selectedCrew.filter(c => c.seniority === 'SENIOR').length;
    const junior = selectedCrew.filter(c => c.seniority === 'JUNIOR').length;
    const chefs = selectedCrew.filter(c => c.type === 'CHEF').length;
    let errors = [];
    if(senior < 1) errors.push("Need 1+ Senior");
    if(senior > 4) errors.push("Max 4 Seniors");
    if(junior < 4) errors.push("Need 4+ Juniors");
    if(junior > 16) errors.push("Max 16 Juniors");
    if(chefs > 2) errors.push("Max 2 Chefs");

    const div = document.getElementById('crewValidationMsg');
    div.innerHTML = errors.length > 0 ? 
        `<span style="color:red"><i class="fas fa-exclamation-triangle"></i> ${errors.join(', ')}</span>` : 
        `<span style="color:green"><i class="fas fa-check-circle"></i> Valid</span>`;
}

function renderTabularView() {
    const tbody = document.getElementById('summaryTableBody');
    tbody.innerHTML = '';
    const all = [
        ...selectedPilots.map(p => ({...p, type: 'Pilot'})),
        ...selectedCrew.map(c => ({...c, type: 'Cabin Crew'})),
        ...(currentRosterData.passengers||[]).map(p => ({...p, type: 'Passenger'}))
    ];
    all.forEach(p => {
        tbody.innerHTML += `<tr><td><b>${p.type}</b></td><td>${p.id}</td><td>${p.name}</td><td>${p.gender||'-'}</td><td>${p.nationality||'-'}</td></tr>`;
    });
}

function renderPlaneView() {
    const cockpit = document.getElementById('cockpitGrid'); cockpit.innerHTML = '';
    selectedPilots.forEach(p => cockpit.appendChild(createSeatElement(p, 'pilot', 'PLT')));
    
    const crewDiv = document.getElementById('cabinCrewGrid'); crewDiv.innerHTML = '';
    selectedCrew.forEach(c => crewDiv.appendChild(createSeatElement(c, 'crew', 'CRW')));

    const grid = document.getElementById('passengerGrid'); grid.innerHTML = '';
    let totalRows = 25;
    try {
        const conf = JSON.parse(currentRosterData.flightInfo.vehicleType.seatingPlanConfig);
        if(conf.rows) totalRows = conf.rows;
    } catch(e){}
    const cols = ['A','B','C','AISLE','D','E','F'];
    const pMap = {};
    (currentRosterData.passengers||[]).forEach(p => { if(p.seatNumber) pMap[p.seatNumber] = p; });

    for(let r=1; r<=totalRows; r++){
        cols.forEach(c => {
            if(c==='AISLE') {
                const sp = document.createElement('div'); sp.className='aisle-spacer'; sp.innerText=r; 
                sp.style.textAlign='center'; sp.style.fontSize='0.7em'; sp.style.color='#ccc';
                grid.appendChild(sp);
            } else {
                const sNum = `${r}${c}`;
                const p = pMap[sNum];
                if(p) {
                    const type = (p.seatType && p.seatType.toLowerCase()==='business')?'business':'economy';
                    grid.appendChild(createSeatElement(p, `${type} occupied`, sNum));
                } else {
                    const s = document.createElement('div'); s.className='seat';
                    s.onmouseenter=(e)=>showTooltip(e,{name:'Empty', seatNumber:sNum}); s.onmouseleave=hideTooltip;
                    grid.appendChild(s);
                }
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
    let c = `<strong>${data.name}</strong>`;
    if(data.nationality) c+=`<br>Nat: ${data.nationality}`;
    if(data.seatNumber) c+=`<br>Seat: ${data.seatNumber}`;
    t.innerHTML = c;
    const r = e.target.getBoundingClientRect();
    const cr = document.querySelector('.plane-container').getBoundingClientRect();
    t.style.left = (r.left - cr.left + r.width/2)+'px';
    t.style.top = (r.top - cr.top - 55)+'px';
    t.style.transform = 'translateX(-50%)';
    t.classList.remove('hidden');
}
function hideTooltip() { document.getElementById('seatTooltip').classList.add('hidden'); }

function renderExtendedView() {
    populateTable(document.getElementById('pilotTable'), selectedPilots, 
        p => `<td>${p.id}</td><td>${p.name}</td><td>${p.seniorityLevel}</td><td>${p.allowedVehicleType}</td><td>${p.age}</td>`);
    
    // FIX: Updated logic to display languages correctly in Extended View
    populateTable(document.getElementById('crewTable'), selectedCrew, c => {
        const langs = c.languages || c.knownLanguages || [];
        let info = langs.length > 0 ? langs.join(', ') : '-';
        if(c.type==='CHEF') {
            const recipe = c.selectedDish || 'Not selected';
            info += `<br><small style="color:#d9534f"><i class="fas fa-utensils"></i> ${recipe}</small>`;
        }
        return `<td>${c.id}</td><td>${c.name}</td><td>${c.type}</td><td>${info}</td><td>${c.age}</td>`;
    });
    
    populateTable(document.getElementById('passengerTable'), currentRosterData.passengers||[], 
        p => `<td>${p.id}</td><td>${p.name}</td><td>${p.seatType}</td><td>${p.seatNumber}</td><td>${p.nationality}</td>`);
}

function populateTable(tbl, data, fmt) {
    const tb = tbl.querySelector('tbody'); tb.innerHTML='';
    if(!data||data.length===0) tb.innerHTML='<tr><td colspan="5">No data</td></tr>';
    else data.forEach(i => { const tr=document.createElement('tr'); tr.innerHTML=fmt(i); tb.appendChild(tr); });
}

function switchView(v) {
    ['tabularView','planeView','extendedView'].forEach(id=>document.getElementById(id).classList.add('hidden'));
    document.getElementById(v+'View').classList.remove('hidden');
    document.querySelectorAll('.view-tabs .tab-btn').forEach(b=>b.classList.remove('active'));
    document.querySelector(`.view-tabs .tab-btn[onclick*="${v}"]`).classList.add('active');
}

function openSaveModal() { 
    if(!currentRosterData){alert("Load roster first");return;}
    document.getElementById('modalFlightId').innerText=currentFlightId;
    document.getElementById('saveModal').style.display='block';
}
function closeModal() { document.getElementById('saveModal').style.display='none'; }

async function saveToDB(type) {
    const token = localStorage.getItem('jwt_token');
    const [u, p] = atob(token).split(':');
    const auth = btoa(`${u}:${p}`);
    try {
        const res = await fetch(`${API_BASE_URL}/roster/generate/${currentFlightId}?dbType=${type}`, 
            { method:'GET', headers:{'Authorization':`Basic ${auth}`} });
        if(res.ok) alert(`Saved to ${type.toUpperCase()}`); else alert("Fail");
    } catch(e) { alert("Error"); }
    closeModal();
}

function exportJson() {
    if(!currentRosterData) return;
    const s = "data:text/json;charset=utf-8,"+encodeURIComponent(JSON.stringify(currentRosterData,null,2));
    const a = document.createElement('a'); a.href=s; a.download=`roster_${currentFlightId}.json`;
    document.body.appendChild(a); a.click(); a.remove();
}