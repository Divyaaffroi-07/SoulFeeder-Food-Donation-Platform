import { render, screen } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { vi, describe, it, expect, beforeEach } from 'vitest'

vi.mock('./api', () => ({
  default: { get: vi.fn(), post: vi.fn(), put: vi.fn(), patch: vi.fn() },
  errMsg: e => e?.response?.data?.message || 'Cannot reach the server',
}))
import api from './api'
import App from './App'

const stats = { TOTAL: 7, AVAILABLE: 3, PICKED_UP: 1, MEALS_DELIVERED: 42 }
const pageOf = { items: [], page: 0, totalPages: 0, total: 0 }
const routes = { '/stats': stats, '/donations': [], '/donations/search': pageOf, '/notifications': [] }

beforeEach(() => {
  sessionStorage.clear()
  api.get.mockReset(); api.post.mockReset()
  api.get.mockImplementation(url => Promise.resolve({ data: routes[url] }))
})

describe('SoulFeeder', () => {
  it('shows the landing page with live numbers from the API', async () => {
    render(<App />)
    expect(screen.getByText(/Good food deserves a plate/)).toBeInTheDocument()
    expect(await screen.findByText('42')).toBeInTheDocument()
  })

  it('asks guests to sign in before they can browse', async () => {
    render(<App />)
    await userEvent.click(screen.getByRole('button', { name: /Browse/ }))
    expect(screen.getByText(/Welcome back/)).toBeInTheDocument()
  })

  it('offers a role choice on the registration form', async () => {
    render(<App />)
    await userEvent.click(screen.getByRole('button', { name: /Browse/ }))
    await userEvent.click(screen.getByRole('button', { name: /Create an account/ }))
    expect(screen.getByText(/Join SoulFeeder/)).toBeInTheDocument()
    expect(screen.getByRole('combobox')).toBeInTheDocument()
  })

  it('shows server field errors under the form field', async () => {
    api.post.mockRejectedValue({ response: { data: { message: 'Please fix the highlighted fields', fields: { email: 'must be a well-formed email address' } } } })
    render(<App />)
    await userEvent.click(screen.getByRole('button', { name: /Browse/ }))
    await userEvent.click(screen.getByRole('button', { name: /Create an account/ }))
    await userEvent.type(screen.getByLabelText(/Your name/), 'Asha')
    await userEvent.type(screen.getByLabelText(/Email/), 'asha@example.com')
    await userEvent.type(screen.getByLabelText(/Password/), 'password123')
    await userEvent.click(screen.getByRole('button', { name: /Create account/ }))
    expect(await screen.findByText('must be a well-formed email address')).toBeInTheDocument()
  })

  it('shows a role-based menu after login: donors see Donate but not Admin', async () => {
    api.post.mockResolvedValue({ data: { token: 't', id: 1, name: 'Asha', email: 'a@x.com', role: 'DONOR' } })
    render(<App />)
    await userEvent.click(screen.getByRole('button', { name: /Browse/ }))
    await userEvent.type(screen.getByLabelText(/Email/), 'a@x.com')
    await userEvent.type(screen.getByLabelText(/Password/), 'password123')
    await userEvent.click(screen.getByRole('button', { name: /✅ Sign in/ }))
    expect(await screen.findByRole('button', { name: /➕ Donate/ })).toBeInTheDocument()
    expect(screen.queryByRole('button', { name: /Admin/ })).not.toBeInTheDocument()
    expect(sessionStorage.getItem('sf_token')).toBe('t')
  })

  it('lets donors type a whole value without losing focus after each key', async () => {
    api.post.mockResolvedValue({ data: { token: 't', id: 1, name: 'Asha', email: 'a@x.com', role: 'DONOR' } })
    render(<App />)
    await userEvent.click(screen.getByRole('button', { name: /Browse/ }))
    await userEvent.type(screen.getByLabelText(/Email/), 'a@x.com')
    await userEvent.type(screen.getByLabelText(/Password/), 'password123')
    await userEvent.click(screen.getByRole('button', { name: /✅ Sign in/ }))
    await userEvent.click(await screen.findByRole('button', { name: /➕ Donate/ }))
    const addr = screen.getByLabelText(/Pickup address/)
    await userEvent.type(addr, '12 Main Road, Salem')
    expect(addr).toHaveValue('12 Main Road, Salem')
    expect(addr).toHaveFocus()
  })
})
