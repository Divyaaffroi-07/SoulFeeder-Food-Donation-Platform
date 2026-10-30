import { useEffect, useState, useCallback } from 'react'
import api, { errMsg } from './api'

const STEPS = [['AVAILABLE','📦','Posted'],['CLAIMED','🤝','Claimed'],['PICKED_UP','🚚','On the way'],['DELIVERED','🎉','Delivered']]
const ICON = { Bakery:'🥖', 'Cooked meal':'🍛', 'Event food':'🎊', Fruits:'🍎', Packaged:'🥫', Other:'🍽️' }
const BADGE = { AVAILABLE:'b-lime', CLAIMED:'b-yellow', PICKED_UP:'b-lav', DELIVERED:'b-green', CANCELLED:'b-coral' }
const ROLE = { DONOR:'🍱', NGO:'🏢', VOLUNTEER:'🛵', ADMIN:'🛡️' }
const VB = { PENDING:'b-yellow', APPROVED:'b-green', REJECTED:'b-coral' }
const NEXT = { CLAIMED:['PICKED_UP','🚚 Mark picked up'], PICKED_UP:['DELIVERED','🎉 Mark delivered'] }
const fmt = d => new Date(d).toLocaleString([], { dateStyle:'medium', timeStyle:'short' })
const localNow = (h=0) => { const d = new Date(Date.now()+h*36e5 - new Date().getTimezoneOffset()*6e4); return d.toISOString().slice(0,16) }
const empty = { title:'', foodType:'Cooked meal', quantity:10, pickupLocation:'', preparedAt:localNow(-1), expiresAt:localNow(6), safetyNotes:'' }

export default function App() {
  const [tab, setTab] = useState('home')
  const [list, setList] = useState(null)
  const [stats, setStats] = useState(null)
  const [filter, setFilter] = useState('')
  const [user, setUser] = useState(() => JSON.parse(sessionStorage.getItem('sf_user') || 'null'))
  const [mine, setMine] = useState(false)
  const [ngo, setNgo] = useState(null)
  const [unread, setUnread] = useState(0)
  const [pg, setPg] = useState(null); const [q, setQ] = useState(''); const [qd, setQd] = useState(''); const [ftype, setFtype] = useState(''); const [sort, setSort] = useState('newest'); const [page, setPage] = useState(0)
  useEffect(() => { const t = setTimeout(() => { setQd(q); setPage(0) }, 300); return () => clearTimeout(t) }, [q])
  const [sel, setSel] = useState(null)
  const [toast, setToast] = useState(null)
  const say = (m, bad) => { setToast({ m, bad }); setTimeout(() => setToast(null), 3500) }

  const logout = useCallback((msg) => { sessionStorage.clear(); setUser(null); setList(null); setPg(null); setTab('home'); if (msg) say(msg, true) }, [])
  useEffect(() => { const f = () => logout('Session expired — please sign in again 🔐'); window.addEventListener('sf-logout', f); return () => window.removeEventListener('sf-logout', f) }, [logout])
  const load = useCallback(async () => {
    const none = { items: [], page: 0, totalPages: 0 }
    try {
      const st = await api.get('/stats'); setStats(st.data)
      if (user) {
        const base = { ...(filter ? { status: filter } : {}), ...(mine ? { mine: true } : {}) }
        const [l, p] = await Promise.all([api.get('/donations', { params: base }), api.get('/donations/search', { params: { ...base, q: qd, foodType: ftype, sort, page, size: 9 } })])
        setList(l.data); setPg(p.data)
      } else { setList([]); setPg(none) }
    } catch (e) { setList([]); setPg(none); say(errMsg(e), true) }
  }, [filter, mine, user, qd, ftype, sort, page])
  useEffect(() => { load() }, [load])
  const loadNgo = useCallback(async () => { if (user?.role !== 'NGO') return setNgo(null); try { const r = await api.get('/ngo/verification'); setNgo(r.data || null) } catch { setNgo(null) } }, [user])
  useEffect(() => { loadNgo() }, [loadNgo])
  useEffect(() => { if (!user) { setUnread(0); return }
    const f = () => api.get('/notifications').then(r => setUnread(r.data.filter(n => !n.seen).length)).catch(() => {})
    f(); const t = setInterval(f, 30000); return () => clearInterval(t) }, [user, tab])

  const act = async (fn, ok) => { try { await fn(); say(ok); await load() } catch (e) { say(errMsg(e), true); load() } }
  const claim = d => act(() => api.post(`/donations/${d.id}/claim`), '🤝 Claimed! Track it in the Tracking tab')
  const move = (d, s) => act(() => api.patch(`/donations/${d.id}/status`, { status: s }), '✅ Status updated')

  return (<>
    <header className="top">
      <div className="logo" onClick={() => setTab('home')}>🍲 SoulFeeder</div>
      <nav>{[['home','🏠 Home'],['browse','🔎 Browse'],...(user?.role==='DONOR'?[['donate','➕ Donate']]:[]),...(user?.role==='NGO'?[['verify','✅ Verification']]:[]),...(user?.role==='ADMIN'?[['admin','🛡️ Admin']]:[]),...(user?.role==='VOLUNTEER'?[['tasks','🛵 Tasks']]:[['track','📍 Tracking']]),...(user?[['inbox','🔔 Inbox'+(unread?` (${unread})`:'')],['profile','👤 Profile']]:[])].map(([k,l]) =>
        <button key={k} className={tab===k?'on':''} onClick={() => setTab(k)}>{l}</button>)}
        {user ? <button onClick={() => logout()}>{ROLE[user.role]} {user.name} · 🚪 Logout</button> : <button className="in" onClick={() => setTab('browse')}>🔐 Sign in</button>}</nav>
    </header>
    {toast && <div className={'toast '+(toast.bad?'bad':'')} role="status">{toast.m}</div>}
    <main>
      {!user && tab!=='home' ? <Auth say={say} onAuth={u => setUser(u)} /> : <>
      {tab==='home' && <Home stats={stats} go={setTab} />}
      {tab==='browse' && <>
        <div className="bar">
          <label className="chk"><input type="checkbox" checked={mine} onChange={e => { setMine(e.target.checked); setPage(0) }} /> 🙋 Only mine</label>
          <input className="grow" placeholder="🔍 Search food, place or donor" value={q} onChange={e => setQ(e.target.value)} aria-label="Search" />
          <select value={ftype} onChange={e => { setFtype(e.target.value); setPage(0) }} aria-label="Food type"><option value="">All food types</option>{Object.keys(ICON).map(k => <option key={k}>{k}</option>)}</select>
          <select value={sort} onChange={e => { setSort(e.target.value); setPage(0) }} aria-label="Sort"><option value="newest">🆕 Newest</option><option value="expiry">⏰ Expiring first</option><option value="quantity">🍽️ Most servings</option></select>
          <select value={filter} onChange={e => { setFilter(e.target.value); setPage(0) }} aria-label="Filter status">
            <option value="">All statuses</option>{Object.keys(BADGE).map(s => <option key={s}>{s}</option>)}</select>
        </div>
        {user?.role==='NGO' && ngo?.status!=='APPROVED' && <div className="notice">🏢 Only verified NGOs can claim food. <button className="ghost sm" onClick={() => setTab('verify')}>✅ {ngo ? 'Check status' : 'Get verified'}</button></div>}
        {pg===null ? <div className="grid">{[1,2,3].map(i => <div key={i} className="card skel" />)}</div>
          : pg.items.length===0 ? <Empty go={setTab} /> :
          <div className="grid">{pg.items.map(d => <Card key={d.id} d={d} canClaim={ngo?.status==='APPROVED'} onClaim={claim} onTrack={() => { setSel(d.id); setTab('track') }} />)}</div>}
        {pg && <Pager page={pg.page} pages={pg.totalPages} go={setPage} />}
      </>}
      {tab==='donate' && <Donate onDone={() => { say('🎉 Donation posted! Thank you'); load(); setTab('browse') }} say={say} />}
      {tab==='verify' && user?.role==='NGO' && <Verify key={ngo?.status || 'new'} ngo={ngo} reload={loadNgo} say={say} />}
      {tab==='admin' && user?.role==='ADMIN' && <Admin say={say} />}
      {tab==='tasks' && user?.role==='VOLUNTEER' && <Tasks say={say} reloadDonations={load} />}
      {tab==='profile' && user && <Profile user={user} setUser={setUser} say={say} />}
      {tab==='inbox' && <Inbox say={say} refresh={() => setUnread(0)} />}
      {tab==='track' && <Track list={list} sel={sel} setSel={setSel} move={move} user={user} />}
      </>}
    </main>
    <footer>🌱 Feed Every Soul · Sample records are labeled 🧪 · Food safety is the donor’s and receiver’s responsibility</footer>
  </>)
}

function Home({ stats, go }) {
  const t = [['📦','Total donations',stats?.TOTAL,'c1'],['🟢','Available now',stats?.AVAILABLE,'c2'],['🚚','In transit',stats?.PICKED_UP,'c3'],['🍽️','Meals delivered',stats?.MEALS_DELIVERED,'c4']]
  return (<section>
    <div className="hero"><div>
      <h1>Good food deserves a plate, not a bin. 🥗</h1>
      <p>Restaurants, bakeries and families post surplus food. NGOs claim it in seconds. Everyone can watch it travel. 🚚💨</p>
      <div className="row"><button className="cta" onClick={() => go('donate')}>🍱 Donate food</button><button className="ghost" onClick={() => go('browse')}>🔎 Find food</button></div></div>
      <div className="blob">🍛🥖🍎<span>🧡</span></div></div>
    <div className="stats">{t.map(([i,l,v,c]) => <div key={l} className={'stat '+c}><span>{i}</span><b>{v ?? '…'}</b><small>{l}</small></div>)}</div>
    <p className="note">📊 These numbers are live from your database — no made-up impact data.</p>
  </section>)
}

function Card({ d, canClaim, onClaim, onTrack }) {
  const mins = (new Date(d.expiresAt) - Date.now()) / 6e4
  return (<article className="card">
    <div className={'pic' + (d.imageName ? ' has' : '')}>{d.imageName ? <img src={`${import.meta.env.VITE_API_URL}/images/${d.imageName}`} alt={d.title} loading="lazy" /> : (ICON[d.foodType] || '🍽️')}</div>
    <div className="body">
      <div className="row between"><span className={'badge '+BADGE[d.status]}>{d.status.replace('_',' ')}</span>{d.sample && <small>🧪 sample</small>}</div>
      <h3>{d.title}</h3>
      <p>🏪 {d.donorName}</p><p>📍 {d.pickupLocation}</p><p>🍽️ {d.quantity} servings · {d.foodType}</p>
      <p className={mins<120?'warn':''}>⏰ Expires {fmt(d.expiresAt)}{mins>0&&mins<120?' — soon!':''}</p>
      {d.claimedBy && <p>🏢 {d.claimedBy}</p>}
      <div className="row">
        {d.status==='AVAILABLE' && canClaim && <button className="cta sm" onClick={() => onClaim(d)}>🤝 Claim</button>}
        <button className="ghost sm" onClick={onTrack}>📍 Track</button></div>
    </div></article>)
}

// Defined OUTSIDE Donate on purpose: a component created inside another component is a brand-new type on every
// render, so React remounts the <input> and it loses focus after each keystroke.
const Fld = ({ f, set, err, k, label, ...p }) => <label>{label}<input value={f[k]} onChange={set(k)} {...p} />{err[k] && <em>{err[k]}</em>}</label>

function Donate({ onDone, say }) {
  const [f, setF] = useState(empty); const [err, setErr] = useState({}); const [busy, setBusy] = useState(false); const [file, setFile] = useState(null)
  const set = k => e => setF({ ...f, [k]: e.target.value })
  const submit = async e => { e.preventDefault(); setBusy(true); setErr({})
    try { const { data } = await api.post('/donations', { ...f, quantity: +f.quantity })
      if (file) { try { const fd = new FormData(); fd.append('file', file); await api.post(`/donations/${data.id}/image`, fd) } catch (x) { say('Donation posted, but the photo failed: ' + errMsg(x), true) } }
      setF(empty); setFile(null); onDone() }
    catch (x) { setErr(x.response?.data?.fields || {}); say(errMsg(x), true) } setBusy(false) }
  return (<form className="form" onSubmit={submit}>
    <h2>🍱 Share surplus food</h2>
    <Fld f={f} set={set} err={err} k="title" label="🥘 What are you donating?" required />
    <label>🍽️ Food type<select value={f.foodType} onChange={set('foodType')}>{Object.keys(ICON).map(k => <option key={k}>{k}</option>)}</select></label>
    <Fld f={f} set={set} err={err} k="quantity" label="🔢 Servings" type="number" min="1" required />
    <Fld f={f} set={set} err={err} k="pickupLocation" label="📍 Pickup address" required />
    <div className="two"><Fld f={f} set={set} err={err} k="preparedAt" label="🍳 Prepared at" type="datetime-local" required /><Fld f={f} set={set} err={err} k="expiresAt" label="⏳ Safe until" type="datetime-local" required /></div>
    <Fld f={f} set={set} err={err} k="safetyNotes" label="🧼 Safety notes (storage, allergens)" maxLength="500" />
    <label>📸 Photo (optional · JPEG/PNG/WebP · max 2 MB)<input type="file" accept="image/jpeg,image/png,image/webp" onChange={e => setFile(e.target.files[0] || null)} /></label>
    <button className="cta" disabled={busy}>{busy ? 'Posting…' : '🚀 Post donation'}</button>
  </form>)
}

function Track({ list, sel, setSel, move, user }) {
  const [hist, setHist] = useState([])
  const [dl, setDl] = useState(null)
  const d = list?.find(x => x.id === sel)
  useEffect(() => { if (sel) api.get(`/donations/${sel}/history`).then(r => setHist(r.data)).catch(() => setHist([])) }, [sel, d?.status])
  useEffect(() => { if (sel) api.get(`/donations/${sel}/delivery`).then(r => setDl(r.data || null)).catch(() => setDl(null)) }, [sel, d?.status])
  const idx = d ? STEPS.findIndex(s => s[0] === d.status) : -1
  return (<div className="trackwrap">
    <aside>{(list||[]).map(x => <button key={x.id} className={x.id===sel?'on':''} onClick={() => setSel(x.id)}>{ICON[x.foodType]||'🍽️'} {x.title}<small>{x.status.replace('_',' ')}</small></button>)}
      {list?.length===0 && <p>Nothing to track yet 🌱</p>}</aside>
    <section>{!d ? <div className="empty">👈 Pick a donation to see its live journey 📍</div> : <>
      <h2>{d.title}</h2>
      {d.status==='CANCELLED' ? <p className="warn">❌ Cancelled</p> :
        <ol className="steps">{STEPS.map(([k,i,l],n) => <li key={k} className={n<=idx?'done':''}><span>{i}</span>{l}</li>)}</ol>}
      <div className="row">{NEXT[d.status] && !(dl && ['ACCEPTED','PICKED_UP'].includes(dl.status)) && (user.role==='ADMIN' || (user.role==='NGO' && d.claimedById===user.id)) && <button className="cta sm" onClick={() => move(d, NEXT[d.status][0])}>{NEXT[d.status][1]}</button>}
        {['AVAILABLE','CLAIMED'].includes(d.status) && (user.role==='ADMIN' || (user.role==='DONOR' && d.donorId===user.id)) && <button className="ghost sm" onClick={() => window.confirm('Cancel this donation?') && move(d,'CANCELLED')}>❌ Cancel</button>}</div>
      {dl && <p className="notice">🛵 Delivery: <b>{dl.status.replace('_',' ')}</b>{dl.volunteerName ? ` · Volunteer ${dl.volunteerName}` : ' · waiting for a volunteer'}<br />📍 {dl.pickupAddress} ➜ 🏢 {dl.dropoffAddress}</p>}
      <h3>🕒 History</h3>
      <ul className="hist">{hist.map(h => <li key={h.id}><b>{h.note}</b><small>{fmt(h.at)}</small></li>)}</ul></>}
    </section></div>)
}
const Empty = ({ go }) => <div className="empty">🌱 No donations here yet.<br /><button className="cta sm" onClick={() => go('donate')}>➕ Post the first one</button></div>

function Auth({ onAuth, say }) {
  const [reg, setReg] = useState(false); const [f, setF] = useState({ name:'', email:'', password:'', role:'DONOR' }); const [err, setErr] = useState({}); const [busy, setBusy] = useState(false)
  const set = k => e => setF({ ...f, [k]: e.target.value })
  const submit = async e => { e.preventDefault(); setBusy(true); setErr({})
    try { const { data } = await api.post(reg ? '/auth/register' : '/auth/login', f)
      sessionStorage.setItem('sf_token', data.token); sessionStorage.setItem('sf_user', JSON.stringify({ id:data.id, name:data.name, role:data.role }))
      onAuth({ id:data.id, name:data.name, role:data.role }); say(`👋 Welcome, ${data.name}!`) }
    catch (x) { setErr(x.response?.data?.fields || {}); say(errMsg(x), true) } setBusy(false) }
  return (<form className="form" onSubmit={submit}>
    <h2>{reg ? '🌟 Join SoulFeeder' : '🔐 Welcome back'}</h2>
    {reg && <label>🙂 Your name<input value={f.name} onChange={set('name')} required />{err.name && <em>{err.name}</em>}</label>}
    <label>📧 Email<input type="email" value={f.email} onChange={set('email')} required autoComplete="email" />{err.email && <em>{err.email}</em>}</label>
    <label>🔑 Password<input type="password" value={f.password} onChange={set('password')} required minLength={reg ? 8 : undefined} autoComplete={reg ? 'new-password' : 'current-password'} />{err.password && <em>{err.password}</em>}</label>
    {reg && <label>🎭 I am a…<select value={f.role} onChange={set('role')}><option value="DONOR">🍱 Donor</option><option value="NGO">🏢 NGO</option><option value="VOLUNTEER">🛵 Volunteer</option></select></label>}
    <button className="cta" disabled={busy}>{busy ? 'Please wait…' : reg ? '🚀 Create account' : '✅ Sign in'}</button>
    <button type="button" className="ghost" onClick={() => { setReg(!reg); setErr({}) }}>{reg ? 'I already have an account' : 'New here? Create an account'}</button>
  </form>)
}

function Verify({ ngo, reload, say }) {
  const [f, setF] = useState({ orgName: ngo?.orgName || '', registrationNumber: ngo?.registrationNumber || '', address: ngo?.address || '', phone: ngo?.phone || '', description: ngo?.description || '' })
  const [err, setErr] = useState({}); const [busy, setBusy] = useState(false)
  const set = k => e => setF({ ...f, [k]: e.target.value })
  const locked = ngo && ngo.status !== 'REJECTED'
  const submit = async e => { e.preventDefault(); setBusy(true); setErr({})
    try { await api.put('/ngo/verification', f); say('📨 Submitted for review'); await reload() } catch (x) { setErr(x.response?.data?.fields || {}); say(errMsg(x), true) } setBusy(false) }
  return (<form className="form" onSubmit={submit}>
    <h2>✅ NGO verification</h2>
    {ngo && <p><span className={'badge ' + VB[ngo.status]}>{ngo.status}</span> {ngo.reviewNote && <>💬 {ngo.reviewNote}</>}</p>}
    {ngo?.status === 'APPROVED' && <p>🎉 You’re verified — head to Browse and claim food.</p>}
    {ngo?.status === 'PENDING' && <p>⏳ An admin will review your details soon.</p>}
    {ngo?.status === 'REJECTED' && <p>🔁 Fix the details below and resubmit.</p>}
    {[['orgName','🏢 Organization name'],['registrationNumber','🧾 Registration number'],['address','📍 Address'],['phone','📞 Phone']].map(([k, l]) =>
      <label key={k}>{l}<input value={f[k]} onChange={set(k)} disabled={locked} required />{err[k] && <em>{err[k]}</em>}</label>)}
    <label>📝 About your work<input value={f.description} onChange={set('description')} disabled={locked} maxLength={500} /></label>
    {!locked && <button className="cta" disabled={busy}>{busy ? 'Sending…' : ngo ? '🔁 Resubmit' : '📨 Submit for review'}</button>}
  </form>)
}

function AdminNgos({ say }) {
  const [rows, setRows] = useState(null); const [audit, setAudit] = useState([]); const [st, setSt] = useState('PENDING')
  const load = useCallback(async () => { try {
    const [a, b] = await Promise.all([api.get('/admin/ngos', { params: st ? { status: st } : {} }), api.get('/admin/audit')]); setRows(a.data); setAudit(b.data)
  } catch (e) { setRows([]); say(errMsg(e), true) } }, [st])
  useEffect(() => { load() }, [load])
  const review = async (r, status) => {
    let note = ''
    if (status === 'REJECTED') { note = window.prompt('Reason for rejection (the NGO will see this):') || ''; if (!note.trim()) return say('A rejection reason is required', true) }
    try { await api.patch(`/admin/ngos/${r.id}/verification`, { status, note }); say(status === 'APPROVED' ? '✅ NGO approved' : '❌ NGO rejected') } catch (e) { say(errMsg(e), true) }
    load() }
  return (<section>
    <div className="row between"><h2>🛡️ NGO verification queue</h2>
      <select value={st} onChange={e => setSt(e.target.value)} style={{ width: 'auto' }} aria-label="Filter"><option value="">All</option>{Object.keys(VB).map(k => <option key={k}>{k}</option>)}</select></div>
    {rows === null ? <div className="card skel" /> : rows.length === 0 ? <div className="empty">🎉 Nothing waiting here.</div> :
      <div className="grid">{rows.map(r => <article className="card" key={r.id}><div className="body">
        <span className={'badge ' + VB[r.status]}>{r.status}</span><h3>🏢 {r.orgName}</h3>
        <p>🧾 {r.registrationNumber}</p><p>📍 {r.address}</p><p>📞 {r.phone}</p><p>👤 {r.userName} · {r.userEmail}</p>
        {r.description && <p>📝 {r.description}</p>}{r.reviewNote && <p>💬 {r.reviewNote}</p>}
        {r.status === 'PENDING' && <div className="row"><button className="cta sm" onClick={() => review(r, 'APPROVED')}>✅ Approve</button><button className="ghost sm" onClick={() => review(r, 'REJECTED')}>❌ Reject</button></div>}
      </div></article>)}</div>}
    <h3>🧾 Audit log</h3>
    <ul className="hist">{audit.length === 0 && <li>No admin actions yet.</li>}{audit.map(a => <li key={a.id}><b>{a.action} — {a.details}</b><small>{a.actorName} · {fmt(a.at)}</small></li>)}</ul>
  </section>)
}

const DB = { OPEN:'b-lime', ACCEPTED:'b-yellow', PICKED_UP:'b-lav', DELIVERED:'b-green', CANCELLED:'b-coral' }

function Tasks({ say, reloadDonations }) {
  const [scope, setScope] = useState('open'); const [rows, setRows] = useState(null); const [prof, setProf] = useState(undefined)
  const [f, setF] = useState({ phone:'', vehicle:'Bike', area:'' }); const [open, setOpen] = useState(null); const [hist, setHist] = useState([])
  const load = useCallback(async () => { try { setRows((await api.get('/volunteer/tasks', { params: { scope } })).data) } catch (e) { setRows([]); say(errMsg(e), true) } }, [scope])
  const loadProf = useCallback(async () => { try { setProf((await api.get('/volunteer/profile')).data || null) } catch { setProf(null) } }, [])
  useEffect(() => { load() }, [load]); useEffect(() => { loadProf() }, [loadProf])
  const run = async (fn, ok) => { try { await fn(); say(ok) } catch (e) { say(errMsg(e), true) } load(); reloadDonations() }
  const saveProf = async e => { e.preventDefault(); try { await api.put('/volunteer/profile', f); say('🛵 Profile saved'); loadProf() } catch (x) { say(errMsg(x), true) } }
  const showHist = async t => { if (open === t.id) return setOpen(null); try { setHist((await api.get(`/deliveries/${t.id}/history`)).data); setOpen(t.id) } catch (e) { say(errMsg(e), true) } }
  return (<section>
    <h2>🛵 Delivery tasks</h2>
    {prof === null && <form className="form" onSubmit={saveProf}><h3>👋 Set up your volunteer profile first</h3>
      <label>📞 Phone<input value={f.phone} onChange={e => setF({ ...f, phone: e.target.value })} required /></label>
      <label>🚲 Vehicle<select value={f.vehicle} onChange={e => setF({ ...f, vehicle: e.target.value })}>{['Bike','Scooter','Car','On foot'].map(v => <option key={v}>{v}</option>)}</select></label>
      <label>📍 Area you cover<input value={f.area} onChange={e => setF({ ...f, area: e.target.value })} required /></label>
      <button className="cta">💾 Save profile</button></form>}
    {prof && <p className="note">🛵 {prof.vehicle} · 📍 {prof.area}</p>}
    <div className="row" style={{ margin: '.8rem 0' }}>
      <button className={scope==='open' ? 'cta sm' : 'ghost sm'} onClick={() => setScope('open')}>🆕 Open tasks</button>
      <button className={scope==='mine' ? 'cta sm' : 'ghost sm'} onClick={() => setScope('mine')}>🙋 My deliveries</button></div>
    {rows === null ? <div className="card skel" /> : rows.length === 0 ? <div className="empty">🌱 {scope === 'open' ? 'No open tasks right now. NGOs create one when they claim food.' : 'You have no deliveries yet.'}</div> :
      <div className="grid">{rows.map(t => <article className="card" key={t.id}><div className="body">
        <span className={'badge ' + DB[t.status]}>{t.status.replace('_',' ')}</span><h3>{t.title}</h3>
        <p>🍽️ {t.quantity} servings · 🏢 {t.ngoName}</p><p>📍 {t.pickupAddress}</p><p>➜ 🏢 {t.dropoffAddress}</p>
        <p className={(new Date(t.expiresAt) - Date.now()) < 72e5 ? 'warn' : ''}>⏰ Food expires {fmt(t.expiresAt)}</p>
        <div className="row">
          {t.status === 'OPEN' && <button className="cta sm" onClick={() => run(() => api.post(`/volunteer/tasks/${t.id}/accept`), '🙋 Task accepted!')}>🙋 Accept</button>}
          {t.status === 'ACCEPTED' && <button className="cta sm" onClick={() => run(() => api.patch(`/deliveries/${t.id}/status`, { status: 'PICKED_UP' }), '🚚 Marked picked up')}>🚚 Picked up</button>}
          {t.status === 'PICKED_UP' && <button className="cta sm" onClick={() => run(() => api.patch(`/deliveries/${t.id}/status`, { status: 'DELIVERED' }), '🎉 Delivered — thank you!')}>🎉 Delivered</button>}
          {scope === 'mine' && <button className="ghost sm" onClick={() => showHist(t)}>🕒 History</button>}</div>
        {open === t.id && <ul className="hist">{hist.map(h => <li key={h.id}><b>{h.note}</b><small>{fmt(h.at)}</small></li>)}</ul>}
      </div></article>)}</div>}
  </section>)
}

function Inbox({ say, refresh }) {
  const [rows, setRows] = useState(null)
  const load = async () => { try { setRows((await api.get('/notifications')).data) } catch (e) { setRows([]); say(errMsg(e), true) } }
  useEffect(() => { load() }, [])
  const readAll = async () => { try { await api.post('/notifications/read-all'); await load(); refresh() } catch (e) { say(errMsg(e), true) } }
  return (<section>
    <div className="row between"><h2>🔔 Notifications</h2><button className="ghost sm" onClick={readAll}>✅ Mark all read</button></div>
    {rows === null ? <div className="card skel" /> : rows.length === 0 ? <div className="empty">📭 Nothing yet. Updates about your donations and deliveries appear here.</div> :
      <ul className="hist">{rows.map(n => <li key={n.id} style={{ opacity: n.seen ? .6 : 1 }}><b>{n.seen ? '' : '🔴 '}{n.message}</b><small>{fmt(n.at)}</small></li>)}</ul>}
  </section>)
}

const Pager = ({ page, pages, go }) => pages > 1 && <div className="row pager"><button className="ghost sm" disabled={page === 0} onClick={() => go(page - 1)}>⬅️ Prev</button><span>Page {page + 1} of {pages}</span><button className="ghost sm" disabled={page + 1 >= pages} onClick={() => go(page + 1)}>Next ➡️</button></div>

function Admin({ say }) {
  const [v, setV] = useState('overview')
  return (<section>
    <div className="row" style={{ marginBottom: '1rem' }}>{[['overview','📊 Overview'],['ngos','🏢 NGOs'],['users','👥 Users']].map(([k, l]) =>
      <button key={k} className={v === k ? 'cta sm' : 'ghost sm'} onClick={() => setV(k)}>{l}</button>)}</div>
    {v === 'overview' && <Overview say={say} />}{v === 'ngos' && <AdminNgos say={say} />}{v === 'users' && <Users say={say} />}
  </section>)
}

function Overview({ say }) {
  const [m, setM] = useState(null)
  useEffect(() => { api.get('/admin/dashboard').then(r => setM(r.data)).catch(e => say(errMsg(e), true)) }, [])
  if (!m) return <div className="card skel" />
  const cards = [['🍽️','Meals delivered',m.mealsDelivered,'c4'],['📦','Servings posted',m.servingsPosted,'c1'],['⏳','NGOs awaiting review',m.ngosPending,'c3'],['🛵','Open delivery tasks',m.openTasks,'c2']]
  const max = Math.max(1, ...Object.values(m.donationsByStatus))
  return (<>
    <div className="stats">{cards.map(([i, l, v, c]) => <div key={l} className={'stat ' + c}><span>{i}</span><b>{v}</b><small>{l}</small></div>)}</div>
    <div className="two">
      <div className="card"><div className="body"><h3>📦 Donations by status</h3>
        {Object.entries(m.donationsByStatus).map(([k, n]) => <div key={k} className="meterrow"><small>{k.replace('_', ' ')} · {n}</small><div className="meter" style={{ width: `${Math.max(4, (n / max) * 100)}%` }} /></div>)}</div></div>
      <div className="card"><div className="body"><h3>👥 Users by role</h3>
        {Object.entries(m.usersByRole).map(([k, n]) => <p key={k}>{ROLE[k]} {k} — <b>{n}</b></p>)}
        <p>✅ Verified NGOs — <b>{m.ngosApproved}</b></p><p>🎉 Completed deliveries — <b>{m.completedDeliveries}</b></p></div></div>
    </div>
    <h3>🏆 Top donors (servings delivered)</h3>
    {m.topDonors.length === 0 ? <div className="empty">Nothing delivered yet — the first delivery will appear here.</div> :
      <ul className="hist">{m.topDonors.map(t => <li key={t.name}><b>{t.name}</b><small>{t.servings} servings</small></li>)}</ul>}
  </>)
}

function Users({ say }) {
  const [d, setD] = useState(null); const [q, setQ] = useState(''); const [role, setRole] = useState(''); const [page, setPage] = useState(0)
  const load = useCallback(async () => { try { setD((await api.get('/admin/users', { params: { q, ...(role ? { role } : {}), page, size: 8 } })).data) } catch (e) { setD({ items: [], page: 0, totalPages: 0 }); say(errMsg(e), true) } }, [q, role, page])
  useEffect(() => { const t = setTimeout(load, 250); return () => clearTimeout(t) }, [load])
  const toggle = async u => {
    const suspend = u.enabled; if (!window.confirm(`${suspend ? 'Suspend' : 'Reinstate'} ${u.name}?`)) return
    try { await api.patch(`/admin/users/${u.id}/suspension`, { suspended: suspend }); say(suspend ? '⛔ User suspended' : '✅ User reinstated') } catch (e) { say(errMsg(e), true) }
    load() }
  return (<>
    <div className="bar"><input className="grow" placeholder="🔍 Search name or email" value={q} onChange={e => { setQ(e.target.value); setPage(0) }} aria-label="Search users" />
      <select value={role} onChange={e => { setRole(e.target.value); setPage(0) }} aria-label="Role"><option value="">All roles</option>{Object.keys(ROLE).map(r => <option key={r}>{r}</option>)}</select></div>
    {d === null ? <div className="card skel" /> : d.items.length === 0 ? <div className="empty">🔎 No users match.</div> :
      d.items.map(u => <div className="urow" key={u.id}><b>{ROLE[u.role]} {u.name}</b><span>{u.email}</span>
        <span className={'badge ' + (u.enabled ? 'b-green' : 'b-coral')}>{u.enabled ? 'Active' : 'Suspended'}</span>
        {u.role !== 'ADMIN' ? <button className="ghost sm" onClick={() => toggle(u)}>{u.enabled ? '⛔ Suspend' : '✅ Reinstate'}</button> : <span />}</div>)}
    {d && <Pager page={d.page} pages={d.totalPages} go={setPage} />}
  </>)
}

function Profile({ user, setUser, say }) {
  const [name, setName] = useState(user.name); const [pw, setPw] = useState({ currentPassword: '', newPassword: '' }); const [err, setErr] = useState({})
  const saveName = async e => { e.preventDefault(); setErr({})
    try { const { data } = await api.put('/users/me', { name }); const u = { ...user, name: data.name }; sessionStorage.setItem('sf_user', JSON.stringify(u)); setUser(u); say('✅ Name updated') } catch (x) { setErr(x.response?.data?.fields || {}); say(errMsg(x), true) } }
  const savePw = async e => { e.preventDefault(); setErr({})
    try { await api.post('/users/me/password', pw); setPw({ currentPassword: '', newPassword: '' }); say('🔑 Password changed') } catch (x) { setErr(x.response?.data?.fields || {}); say(errMsg(x), true) } }
  return (<>
    <form className="form" onSubmit={saveName}><h2>👤 Your profile</h2><p><span className="badge b-lav">{ROLE[user.role]} {user.role}</span></p>
      <label>🙂 Display name<input value={name} onChange={e => setName(e.target.value)} required maxLength={80} />{err.name && <em>{err.name}</em>}</label>
      <button className="cta sm">💾 Save name</button></form>
    <form className="form" style={{ marginTop: '1rem' }} onSubmit={savePw}><h3>🔑 Change password</h3>
      <label>Current password<input type="password" value={pw.currentPassword} onChange={e => setPw({ ...pw, currentPassword: e.target.value })} required autoComplete="current-password" /></label>
      <label>New password (8+ characters)<input type="password" value={pw.newPassword} onChange={e => setPw({ ...pw, newPassword: e.target.value })} required minLength={8} autoComplete="new-password" />{err.newPassword && <em>{err.newPassword}</em>}</label>
      <button className="cta sm">🔐 Update password</button></form>
  </>)
}
