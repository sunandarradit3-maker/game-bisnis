const money = new Intl.NumberFormat('id-ID', { style: 'currency', currency: 'IDR', maximumFractionDigits: 0 });
const compactMoney = (value) => {
  if (value >= 1e12) return `Rp${(value / 1e12).toFixed(1).replace('.', ',')} T`;
  if (value >= 1e9) return `Rp${(value / 1e9).toFixed(1).replace('.', ',')} M`;
  if (value >= 1e6) return `Rp${(value / 1e6).toFixed(1).replace('.', ',')} jt`;
  return money.format(value);
};

const defaultState = {
  playerName: 'Rookie',
  cash: 250_000_000,
  company: null,
  investments: 0,
  partners: [],
  mission: { company: false, transaction: false, partner: false, claimed: false },
  character: { x: 388, y: 335 },
  sound: true
};

let state = loadState();
let sceneScale = window.innerWidth < 620 ? 0.58 : window.innerWidth < 900 ? 0.7 : window.innerWidth < 1180 ? 0.82 : 0.92;
let toastTimer;

const companies = [
  { name: 'Garuda Capital', owner: 'RakaWijaya', value: 8_420_000_000_000, growth: 12.4, tag: 'GC' },
  { name: 'Nusantara Tech', owner: 'NayaGroup', value: 7_850_000_000_000, growth: 9.8, tag: 'NT' },
  { name: 'Atlas Logistics', owner: 'DimasCorp', value: 6_930_000_000_000, growth: 7.1, tag: 'AL' },
  { name: 'Sagara Foods', owner: 'ArdiCEO', value: 5_760_000_000_000, growth: 6.9, tag: 'SF' },
  { name: 'Merah Putih Retail', owner: 'KarinBoss', value: 4_910_000_000_000, growth: 11.2, tag: 'MR' },
  { name: 'Quantum Works', owner: 'BimoLabs', value: 3_880_000_000_000, growth: -2.1, tag: 'QW' },
  { name: 'Archipelago Media', owner: 'TikaStudio', value: 2_670_000_000_000, growth: 4.3, tag: 'AM' },
  { name: 'Borneo Energy', owner: 'FahmiEnergy', value: 1_980_000_000_000, growth: 3.7, tag: 'BE' },
  { name: 'Cendana Property', owner: 'VinaEstate', value: 1_420_000_000_000, growth: 5.4, tag: 'CP' }
];

const market = [
  ['FOOD', '+5,4%', false], ['TECH', '+12,8%', false], ['RETAIL', '-1,6%', true], ['LOGISTICS', '+3,2%', false], ['PROPERTY', '+7,1%', false], ['ENERGY', '-2,4%', true], ['MEDIA', '+1,9%', false]
];

const buildingData = {
  'NEXA Tower': { description: 'Sektor teknologi sedang tumbuh +12,4% minggu ini.', cost: 120_000_000, roi: 18 },
  'Capital Bank': { description: 'Pinjaman modal dan layanan investasi untuk ekspansi perusahaan.', cost: 180_000_000, roi: 14 },
  'Urban Mart': { description: 'Retail punya permintaan stabil dan cocok untuk perusahaan pertama.', cost: 80_000_000, roi: 12 },
  'Hot Plate': { description: 'Kuliner sedang ramai karena event festival kota.', cost: 65_000_000, roi: 16 },
  'Logistik Hub': { description: 'Kuasai distribusi barang dan jadilah pemasok bisnis pemain lain.', cost: 150_000_000, roi: 15 }
};

function loadState() {
  try {
    return { ...defaultState, ...JSON.parse(localStorage.getItem('businessRivalsDemo') || '{}') };
  } catch {
    return structuredClone(defaultState);
  }
}
function saveState() { localStorage.setItem('businessRivalsDemo', JSON.stringify(state)); }
function el(id) { return document.getElementById(id); }
function showToast(message) {
  const toast = el('toast');
  toast.textContent = message;
  toast.classList.add('show');
  clearTimeout(toastTimer);
  toastTimer = setTimeout(() => toast.classList.remove('show'), 2600);
}
function initials(name) { return name.split(/\s+/).map(word => word[0]).join('').slice(0, 2).toUpperCase(); }

function renderTicker() {
  const items = [...market, ...market].map(([symbol, change, down]) => `<span class="ticker-item ${down ? 'down' : ''}"><strong>${symbol}</strong><em>${change}</em></span>`).join('');
  el('tickerTrack').innerHTML = items;
}

function userCompanyEntry() {
  if (!state.company) return null;
  return {
    name: state.company.name,
    owner: state.playerName,
    value: state.company.value,
    growth: state.company.growth,
    tag: initials(state.company.name),
    isPlayer: true
  };
}

function getRankedCompanies() {
  const list = [...companies];
  const player = userCompanyEntry();
  if (player) list.push(player);
  return list.sort((a, b) => b.value - a.value);
}

function renderLeaderboard() {
  const ranked = getRankedCompanies();
  const visible = ranked.slice(0, 4);
  if (state.company && !visible.some(c => c.isPlayer)) visible.push(ranked.find(c => c.isPlayer));
  el('leaderboard').innerHTML = visible.map((company, index) => {
    const rank = ranked.indexOf(company) + 1;
    const medal = rank <= 3 ? ['🥇', '🥈', '🥉'][rank - 1] : rank;
    return `<div class="rank-row ${company.isPlayer ? 'player-row' : ''}">
      <span class="rank-number ${rank <= 3 ? 'medal' : ''}">${medal}</span>
      <span class="company-badge">${company.tag}</span>
      <span class="rank-info"><strong>${company.name}</strong><small>${company.owner}</small></span>
      <span class="rank-value"><strong>${compactMoney(company.value)}</strong><small style="color:${company.growth < 0 ? 'var(--danger)' : 'var(--cyan)'}">${company.growth >= 0 ? '▲' : '▼'} ${Math.abs(company.growth).toFixed(1)}%</small></span>
    </div>`;
  }).join('');

  el('fullLeaderboard').innerHTML = ranked.map((company, index) => `<div class="rank-row ${company.isPlayer ? 'player-row' : ''}">
    <span class="rank-number ${index < 3 ? 'medal' : ''}">${index < 3 ? ['🥇','🥈','🥉'][index] : index + 1}</span>
    <span class="company-badge">${company.tag}</span>
    <span class="rank-info"><strong>${company.name}</strong><small>CEO ${company.owner}${company.isPlayer ? ' · Kamu' : ''}</small></span>
    <span class="rank-value"><strong>${compactMoney(company.value)}</strong><small>${company.growth >= 0 ? '▲' : '▼'} ${Math.abs(company.growth).toFixed(1)}%</small></span>
  </div>`).join('');
}

function renderState() {
  const companyValue = state.company?.value || 0;
  el('headerName').textContent = state.playerName;
  el('characterName').textContent = state.playerName;
  el('netWorth').textContent = money.format(state.cash + companyValue + state.investments);
  el('cashValue').textContent = compactMoney(state.cash);
  el('companyValue').textContent = compactMoney(companyValue);
  el('playerCharacter').style.left = `${state.character.x}px`;
  el('playerCharacter').style.top = `${state.character.y}px`;
  el('soundBtn').textContent = state.sound ? '🔊' : '🔇';

  if (state.company) {
    el('emptyCompany').classList.add('hidden');
    el('activeCompany').classList.remove('hidden');
    el('companyTag').textContent = state.company.name.toUpperCase();
    el('companyLogo').textContent = initials(state.company.name);
    el('companyIndustry').textContent = state.company.industry.toUpperCase();
    el('companyName').textContent = state.company.name;
    el('companyLevel').textContent = `Level ${state.company.level}`;
    el('employeeCount').textContent = `${state.company.employees} karyawan`;
    el('revenueRate').textContent = compactMoney(state.company.revenueRate);
    el('reputation').textContent = state.company.reputation;
  } else {
    el('emptyCompany').classList.remove('hidden');
    el('activeCompany').classList.add('hidden');
    el('companyTag').textContent = 'BELUM PUNYA PT';
  }

  const missionDone = Object.entries(state.mission).filter(([key, value]) => key !== 'claimed' && value).length;
  el('missionProgress').textContent = `${missionDone}/3`;
  el('missionBar').style.width = `${missionDone / 3 * 100}%`;
  el('claimMission').disabled = missionDone < 3 || state.mission.claimed;
  el('claimMission').textContent = state.mission.claimed ? 'Sudah diklaim' : 'Klaim Rp50 jt';
  renderLeaderboard();
  saveState();
}

function openModal(id) { el(id).classList.remove('hidden'); }
function closeModal(id) { el(id).classList.add('hidden'); }

function updateCountdown() {
  const seasonEnd = new Date('2026-09-17T23:59:59+07:00');
  let diff = Math.max(0, seasonEnd - new Date());
  const days = Math.floor(diff / 86400000); diff %= 86400000;
  const hours = Math.floor(diff / 3600000); diff %= 3600000;
  const minutes = Math.floor(diff / 60000);
  el('countdown').textContent = `${days}H ${hours}J ${minutes}M`;
}

function showBuilding(button) {
  const name = button.dataset.building;
  const data = buildingData[name];
  el('buildingName').textContent = name;
  el('buildingType').textContent = data.description;
  el('buildingCost').textContent = compactMoney(data.cost);
  el('buildingRoi').textContent = `${data.roi}%`;
  el('investBtn').dataset.building = name;
  el('buildingCard').classList.remove('hidden');
}

function invest(buildingName) {
  const data = buildingData[buildingName];
  const demoCost = Math.round(data.cost * 0.12);
  if (!state.company) {
    showToast('Buat perusahaan dulu sebelum melakukan investasi.');
    openModal('companyModal');
    return;
  }
  if (state.cash < demoCost) return showToast('Uang tunai belum cukup untuk investasi ini.');
  state.cash -= demoCost;
  state.investments += Math.round(demoCost * (1 + data.roi / 100));
  state.company.value += Math.round(demoCost * 1.3);
  state.company.growth = Math.min(99, state.company.growth + 1.2);
  state.mission.transaction = true;
  renderState();
  showToast(`Investasi di ${buildingName} berhasil. Valuasi perusahaan naik!`);
}

function moveCharacter(dx, dy) {
  const nextX = Math.max(150, Math.min(690, state.character.x + dx));
  const nextY = Math.max(145, Math.min(455, state.character.y + dy));
  state.character = { x: nextX, y: nextY };
  const player = el('playerCharacter');
  player.classList.add('walking');
  renderState();
  setTimeout(() => player.classList.remove('walking'), 300);
}

function createCompany(event) {
  event.preventDefault();
  const name = el('companyNameInput').value.trim();
  const industry = new FormData(event.target).get('industry');
  if (!name) return;
  const startupCost = 100_000_000;
  if (!state.company && state.cash < startupCost) return showToast('Modal awal belum cukup.');
  if (!state.company) state.cash -= startupCost;
  state.company = {
    name,
    industry,
    value: state.company?.value || 145_000_000,
    growth: state.company?.growth || 3.2,
    level: state.company?.level || 1,
    employees: state.company?.employees || 4,
    revenueRate: state.company?.revenueRate || 2_400_000,
    reputation: state.company?.reputation || 52
  };
  state.mission.company = true;
  closeModal('companyModal');
  renderState();
  showToast(`${name} resmi berdiri. Saatnya kuasai kota!`);
}

function runBusiness() {
  if (!state.company) return;
  const revenue = Math.round(state.company.revenueRate * (1.5 + Math.random() * 2.5));
  state.cash += revenue;
  state.company.value += Math.round(revenue * 1.8);
  state.company.reputation = Math.min(100, state.company.reputation + Math.floor(Math.random() * 3) + 1);
  state.company.growth = Math.min(99, state.company.growth + Math.random() * 1.1);
  if (state.company.value > state.company.level * 450_000_000) {
    state.company.level += 1;
    state.company.employees += 3;
    state.company.revenueRate = Math.round(state.company.revenueRate * 1.35);
    showToast(`Naik ke level ${state.company.level}! Pendapatan per jam meningkat.`);
  } else {
    showToast(`Operasi berhasil: perusahaan menghasilkan ${compactMoney(revenue)}.`);
  }
  state.mission.transaction = true;
  renderState();
}

function triggerMarketEvent() {
  if (!state.company) return showToast('Buat perusahaan agar bisa terpengaruh event pasar.');
  const events = [
    ['Festival kota menaikkan permintaan. Omzet melonjak!', 1.12],
    ['Kompetitor melakukan perang harga. Pertumbuhan melambat.', .94],
    ['Investor asing masuk ke kota. Valuasi naik!', 1.18],
    ['Gangguan distribusi membuat biaya operasional membengkak.', .91]
  ];
  const [message, factor] = events[Math.floor(Math.random() * events.length)];
  state.company.value = Math.max(80_000_000, Math.round(state.company.value * factor));
  state.company.growth += factor > 1 ? 2.5 : -2.5;
  renderState();
  showToast(message);
}

function invitePartner(name) {
  if (!state.company) {
    closeModal('partnerModal');
    openModal('companyModal');
    return showToast('Dirikan perusahaan dulu sebelum mengundang partner.');
  }
  if (state.partners.includes(name)) return showToast(`${name} sudah menjadi partner kamu.`);
  state.partners.push(name);
  state.company.value += 35_000_000;
  state.company.reputation = Math.min(100, state.company.reputation + 5);
  state.mission.partner = true;
  closeModal('partnerModal');
  renderState();
  showToast(`${name} menerima undangan partner bisnis!`);
}

function bindEvents() {
  document.querySelectorAll('.building').forEach(button => button.addEventListener('click', () => showBuilding(button)));
  document.querySelectorAll('[data-close-modal]').forEach(button => button.addEventListener('click', () => closeModal(button.dataset.closeModal)));
  document.querySelectorAll('.modal-backdrop').forEach(backdrop => backdrop.addEventListener('click', event => { if (event.target === backdrop) closeModal(backdrop.id); }));
  document.querySelectorAll('.partner-list button').forEach(button => button.addEventListener('click', () => invitePartner(button.dataset.partner)));
  document.querySelectorAll('.nav-item').forEach(button => button.addEventListener('click', () => {
    document.querySelectorAll('.nav-item').forEach(item => item.classList.remove('active'));
    button.classList.add('active');
    const labels = { city: 'Financial District', company: 'Pusat Perusahaan', market: 'Bursa Kota', partners: 'Jaringan Partner', season: 'Season Arena' };
    el('viewTitle').textContent = labels[button.dataset.view];
    if (button.dataset.view === 'company') state.company ? el('activeCompany').scrollIntoView({ behavior: 'smooth', block: 'center' }) : openModal('companyModal');
    if (button.dataset.view === 'partners') openModal('partnerModal');
    if (button.dataset.view === 'season') openModal('leaderboardModal');
    if (button.dataset.view === 'market') triggerMarketEvent();
  }));

  el('createCompanyBtn').addEventListener('click', () => openModal('companyModal'));
  el('editCompanyBtn').addEventListener('click', () => { if (state.company) el('companyNameInput').value = state.company.name; openModal('companyModal'); });
  el('companyForm').addEventListener('submit', createCompany);
  el('runBusinessBtn').addEventListener('click', runBusiness);
  el('marketEventBtn').addEventListener('click', triggerMarketEvent);
  el('showFullLeaderboard').addEventListener('click', () => openModal('leaderboardModal'));
  el('refreshLeaderboard').addEventListener('click', () => { companies.forEach(c => { c.value = Math.round(c.value * (.995 + Math.random() * .02)); c.growth += (Math.random() - .5); }); renderLeaderboard(); showToast('Peringkat dunia diperbarui.'); });
  el('closeBuilding').addEventListener('click', () => el('buildingCard').classList.add('hidden'));
  el('investBtn').addEventListener('click', event => invest(event.currentTarget.dataset.building));
  el('soundBtn').addEventListener('click', () => { state.sound = !state.sound; renderState(); showToast(state.sound ? 'Suara game diaktifkan.' : 'Suara game dimatikan.'); });
  el('profileBtn').addEventListener('click', () => showToast('Profil demo: kosmetik karakter akan hadir di versi berikutnya.'));
  el('claimMission').addEventListener('click', () => { if (el('claimMission').disabled) return; state.cash += 50_000_000; state.mission.claimed = true; renderState(); showToast('Hadiah misi Rp50 juta berhasil diklaim!'); });

  el('zoomIn').addEventListener('click', () => setSceneScale(Math.min(1.15, sceneScale + .08)));
  el('zoomOut').addEventListener('click', () => setSceneScale(Math.max(.45, sceneScale - .08)));
  el('resetCamera').addEventListener('click', () => { const base = window.innerWidth < 620 ? .58 : window.innerWidth < 900 ? .7 : window.innerWidth < 1180 ? .82 : .92; setSceneScale(base); });

  window.addEventListener('keydown', event => {
    if (['INPUT', 'TEXTAREA'].includes(document.activeElement.tagName)) return;
    const keyMap = { ArrowUp: [0,-18], w:[0,-18], W:[0,-18], ArrowDown:[0,18], s:[0,18], S:[0,18], ArrowLeft:[-18,0], a:[-18,0], A:[-18,0], ArrowRight:[18,0], d:[18,0], D:[18,0] };
    if (keyMap[event.key]) { event.preventDefault(); moveCharacter(...keyMap[event.key]); }
  });
}

function setSceneScale(value) {
  sceneScale = value;
  el('isoScene').style.transform = `translate(-50%, -50%) scale(${sceneScale})`;
}

renderTicker();
renderState();
bindEvents();
updateCountdown();
setInterval(updateCountdown, 60_000);
setTimeout(() => showToast('Selamat datang di demo Business Rivals Online.'), 500);
