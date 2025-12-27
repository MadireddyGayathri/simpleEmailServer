let currentUser = null;

function showToast(msg, time=3000){ const t = document.getElementById('toast'); t.innerText = msg; t.classList.remove('hidden'); setTimeout(()=>t.classList.add('hidden'), time); }
function showSpinner(){ document.getElementById('spinner').classList.remove('hidden'); }
function hideSpinner(){ document.getElementById('spinner').classList.add('hidden'); }

document.getElementById('btnReg').onclick = async () => {
  const email = document.getElementById('regEmail').value.trim();
  const password = document.getElementById('regPass').value;
  const emailRegex = /^[\w.%+\-]+@[A-Za-z0-9.-]+\.[A-Za-z]{2,}$/;
  if (!emailRegex.test(email)) { showToast('Invalid email format'); return; }
  showSpinner();
  const res = await fetch('/api/register', {method:'POST', headers:{'Content-Type':'application/json'}, body: JSON.stringify({email,password})});
  hideSpinner();
  const j = await res.json();
  if (j.success) { showToast('Registered'); } else { showToast(j.message || 'Registration failed'); }
};

document.getElementById('btnLogin').onclick = async () => {
  const email = document.getElementById('loginEmail').value;
  const password = document.getElementById('loginPass').value;
  showSpinner();
  const res = await fetch('/api/login', {method:'POST', headers:{'Content-Type':'application/json'}, body: JSON.stringify({email,password})});
  hideSpinner();
  const j = await res.json();
  if (j.success) { currentUser = email; localStorage.setItem('token', j.token); document.getElementById('headerUser').innerText = email; document.getElementById('btnLogout').classList.remove('hidden'); onLogin(); showToast('Logged in'); } else showToast('Login failed');
};

function onLogin(){
  document.getElementById('auth').style.display='none';
  document.getElementById('main').style.display='block';
  document.getElementById('userEmail').innerText = currentUser;
  document.getElementById('btnLogin').disabled = true;
  document.getElementById('btnReg').disabled = true;
  document.getElementById('subject').addEventListener('input', () => {
    document.getElementById('btnMl').disabled = document.getElementById('subject').value.trim()==='';
  });
  document.getElementById('btnMl').disabled = document.getElementById('subject').value.trim()==='';
  loadInbox(); loadSent();
}

// try restore previous session
(function(){ const token = localStorage.getItem('token'); if (token) {
  // we don't have email saved, try to fetch inbox as a quick validation
  fetch('/api/inbox', {headers:{'X-Auth-Token': token}}).then(r=>{ if (r.status==200) { r.json().then(arr => { if (arr.length>0) { /* cannot infer email */ } }); }}).catch(()=>{});
}})();

document.getElementById('btnLogout').onclick = () => { currentUser=null; localStorage.removeItem('token'); document.getElementById('auth').style.display='block'; document.getElementById('main').style.display='none'; document.getElementById('btnLogin').disabled = false; document.getElementById('btnReg').disabled = false; document.getElementById('headerUser').innerText = 'Not signed in'; document.getElementById('btnLogout').classList.add('hidden'); showToast('Logged out'); };

document.getElementById('btnMl').onclick = async () => {
  const subject = document.getElementById('subject').value;
  if (!subject) { showToast('Enter a subject'); return; }
  showSpinner();
  const res = await fetch('/api/ml?subject=' + encodeURIComponent(subject));
  hideSpinner();
  const j = await res.json();
  if (j.body) { document.getElementById('body').value = j.body; showToast('ML suggestion loaded'); } else { showToast('No suggestion available'); }
};

document.getElementById('btnSend').onclick = async () => {
  const to = document.getElementById('to').value.trim();
  const subject = document.getElementById('subject').value;
  const body = document.getElementById('body').value;
  if (!currentUser) { showToast('Login first'); return; }
  const emailRegex = /^[A-Za-z0-9._%+\-]+@[A-Za-z0-9.-]+\.[A-Za-z]{2,}$/;
  if (!emailRegex.test(to)) { showToast('Invalid recipient email'); return; }
  const token = localStorage.getItem('token');
  showSpinner();
  const res = await fetch('/api/send', {method:'POST', headers:{'Content-Type':'application/json','X-Auth-Token': token}, body: JSON.stringify({to, subject, body})});
  hideSpinner();
  const j = await res.json();
  if (j.success) { showToast('Sent'); loadInbox(); loadSent(); } else showToast(j.message || 'Failed to send');
};

async function loadInbox(){
  const token = localStorage.getItem('token');
  const res = await fetch('/api/inbox', {headers:{'X-Auth-Token': token}});
  const arr = await res.json();
  document.getElementById('inboxCount').innerText = arr.length;
  const div = document.getElementById('inboxList'); div.innerHTML='';
  arr.forEach(m => {
    const el = document.createElement('div'); el.className='mail'; el.innerHTML = `<b>From:</b> ${m.from}<br><b>Subject:</b> ${m.subject}<pre>${m.body}</pre><small>${m.time}</small>`;
    div.appendChild(el);
  });
}

async function loadSent(){
  const token = localStorage.getItem('token');
  const res = await fetch('/api/sent', {headers:{'X-Auth-Token': token}});
  const arr = await res.json();
  document.getElementById('sentCount').innerText = arr.length;
  const div = document.getElementById('sentList'); div.innerHTML='';
  arr.forEach(m => {
    const el = document.createElement('div'); el.className='mail'; el.innerHTML = `<b>To:</b> ${m.to}<br><b>Subject:</b> ${m.subject}<pre>${m.body}</pre><small>${m.time}</small>`;
    div.appendChild(el);
  });
}