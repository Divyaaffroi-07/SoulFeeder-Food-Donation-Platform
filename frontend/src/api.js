import axios from 'axios'
const api = axios.create({ baseURL: import.meta.env.VITE_API_URL })
// Token lives in sessionStorage: cleared when the tab closes, but readable by any script on the page (XSS risk).
// Production-hardening option: httpOnly + Secure + SameSite cookies set by the backend.
api.interceptors.request.use(c => { const t = sessionStorage.getItem('sf_token'); if (t) c.headers.Authorization = `Bearer ${t}`; return c })
api.interceptors.response.use(r => r, e => {
  if (e.response?.status === 401 && sessionStorage.getItem('sf_token')) window.dispatchEvent(new Event('sf-logout'))
  return Promise.reject(e) })
export const errMsg = e => e.response?.data?.message || (e.response?.status === 403 ? 'You are not allowed to do that' : 'Cannot reach the server. Is the backend running on port 8080?')
export default api
