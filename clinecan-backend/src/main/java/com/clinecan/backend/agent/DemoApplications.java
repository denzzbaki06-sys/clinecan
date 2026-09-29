package com.clinecan.backend.agent;
import com.clinecan.backend.model.ProjectSpecification;

/** Deterministic, request-sensitive demo source. Never compiled or executed by the agent. */
final class DemoApplications {
    private DemoApplications() {}
    static String app(ProjectSpecification spec) {
        String title = switch(spec.projectName()) { case "expense-tracker" -> "Expense studio"; case "task-manager" -> "Task studio"; case "portfolio-website" -> "Developer portfolio"; default -> "Your next project"; };
        String description = spec.description().replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;").replace("{", "&#123;").replace("}", "&#125;");
        String header = "<header><small>CLINECAN · DEMO STARTER</small><h1>" + title + "</h1><p>" + description + "</p></header>";
        String body = switch(spec.projectName()) {
            case "expense-tracker" -> EXPENSE;
            case "task-manager" -> TASKS;
            case "portfolio-website" -> PORTFOLIO;
            default -> "export default function App() { return <main>__HEADER__<section><h2>Project overview</h2><p>Customize this starter to build your idea. This is deterministic demo output.</p></section></main> }";
        };
        return "import './App.css'\n" + body.replace("__HEADER__", header);
    }
    private static final String EXPENSE = """
        import { useState } from 'react'
        type Expense = { id: number; label: string; amount: number; category: string; month: string }
        export default function App() {
          const [expenses, setExpenses] = useState<Expense[]>([
            { id: 1, label: 'Groceries', amount: 125, category: 'Food', month: '2026-09' },
            { id: 2, label: 'Train pass', amount: 60, category: 'Transport', month: '2026-09' },
            { id: 3, label: 'Books', amount: 45, category: 'Learning', month: '2026-08' }
          ])
          const [month, setMonth] = useState('2026-09')
          const [label, setLabel] = useState('')
          const [amount, setAmount] = useState('')
          const [category, setCategory] = useState('Food')
          const visible = expenses.filter(e => e.month === month)
          const total = visible.reduce((sum, e) => sum + e.amount, 0)
          return <main>__HEADER__
            <section><h2>Monthly analytics</h2><label>Month <input type="month" value={month} onChange={e => setMonth(e.target.value)} /></label><h3>${total.toFixed(2)} total</h3>
              <div aria-label="Category chart">{['Food', 'Transport', 'Learning', 'Other'].map(c => {
                const subtotal = visible.filter(e => e.category === c).reduce((sum, e) => sum + e.amount, 0)
                return <div key={c}><p>{c}: ${subtotal.toFixed(2)}</p><meter aria-label={c + ' share of monthly spending'} min={0} max={Math.max(total, 1)} value={subtotal} style={{ width: '100%' }} /></div>
              })}</div>
            </section>
            <form onSubmit={e => { e.preventDefault(); if (!label.trim() || !(Number(amount) > 0) || !month) return; setExpenses([...expenses, { id: Date.now(), label: label.trim(), amount: Number(amount), category, month }]); setLabel(''); setAmount('') }}>
              <h2>Add expense</h2><input aria-label="Expense name" placeholder="Expense name" value={label} onChange={e => setLabel(e.target.value)} required />
              <input aria-label="Amount" type="number" min="0.01" step="0.01" value={amount} onChange={e => setAmount(e.target.value)} required />
              <select aria-label="Category" value={category} onChange={e => setCategory(e.target.value)}>{['Food', 'Transport', 'Learning', 'Other'].map(c => <option key={c}>{c}</option>)}</select><button>Add expense</button>
            </form>
            <section><h2>Expenses</h2>{visible.length ? visible.map(e => <article key={e.id}><span>{e.label} · {e.category}</span><strong>${e.amount.toFixed(2)}</strong><button onClick={() => setExpenses(expenses.filter(item => item.id !== e.id))}>Delete</button></article>) : <p>No expenses this month.</p>}</section>
            <footer>Sample data · changes are kept in memory only.</footer>
          </main>
        }
        """;
    private static final String TASKS = """
        import { useState } from 'react'
        type Task = { id: number; title: string; priority: string; done: boolean; deadline: string }
        export default function App() {
          const [tasks, setTasks] = useState<Task[]>([{ id: 1, title: 'Plan the next release', priority: 'High', done: false, deadline: '2026-09-30' }, { id: 2, title: 'Review documentation', priority: 'Normal', done: true, deadline: '2026-10-02' }])
          const [title, setTitle] = useState('')
          const [priority, setPriority] = useState('Normal')
          const [deadline, setDeadline] = useState('')
          const [filter, setFilter] = useState('All')
          const visible = tasks.filter(t => filter === 'All' || (filter === 'Completed' ? t.done : !t.done))
          return <main>__HEADER__
            <form onSubmit={e => { e.preventDefault(); if (!title.trim()) return; setTasks([...tasks, { id: Date.now(), title: title.trim(), priority, deadline, done: false }]); setTitle('') }}>
              <h2>New task</h2><input aria-label="Task title" placeholder="What needs doing?" value={title} onChange={e => setTitle(e.target.value)} required />
              <select aria-label="Priority" value={priority} onChange={e => setPriority(e.target.value)}>{['Low', 'Normal', 'High'].map(p => <option key={p}>{p}</option>)}</select><label>Deadline <input type="date" value={deadline} onChange={e => setDeadline(e.target.value)} /></label><button>Add task</button>
            </form>
            <section><h2>Your tasks</h2><label>Status <select value={filter} onChange={e => setFilter(e.target.value)}>{['All', 'Active', 'Completed'].map(f => <option key={f}>{f}</option>)}</select></label>
              {visible.map(t => <article key={t.id}><label><input type="checkbox" checked={t.done} onChange={() => setTasks(tasks.map(item => item.id === t.id ? { ...item, done: !item.done } : item))} /> {t.title}</label><span>{t.priority} · {t.deadline || 'No deadline'}</span><button onClick={() => setTasks(tasks.filter(item => item.id !== t.id))}>Delete</button></article>)}
              {!visible.length && <p>No tasks in this view.</p>}
            </section><footer>Sample tasks · changes are kept in memory only.</footer>
          </main>
        }
        """;
    private static final String PORTFOLIO = """
        const projects = [{ title: 'Campus planner', description: 'A student scheduling interface.' }, { title: 'Open-source toolkit', description: 'Reusable components and developer utilities.' }, { title: 'Data explorer', description: 'Interactive visualizations of public datasets.' }]
        export default function App() {
          return <main>__HEADER__
            <nav><a href="#projects">Projects</a><a href="#skills">Skills</a><a href="#contact">Contact</a></nav>
            <section><h2>Software engineering student</h2><p>I build thoughtful software and learn by shipping. Replace this sample biography with your story.</p></section>
            <section id="projects"><h2>Selected projects</h2>{projects.map(p => <article key={p.title}><div><h3>{p.title}</h3><p>{p.description}</p></div><span>Sample project</span></article>)}</section>
            <section id="skills"><h2>Skills</h2><ul>{['React', 'TypeScript', 'Java', 'Git', 'Problem solving'].map(s => <li key={s}>{s}</li>)}</ul></section>
            <section id="contact"><h2>Let’s build something</h2><p>Replace the sample address before publishing.</p><a href="mailto:you@example.com">you@example.com</a></section>
            <footer>Portfolio starter · sample biography and projects.</footer>
          </main>
        }
        """;
    static final String CSS = """
        :root { font-family: system-ui, sans-serif; background: #100b09; color: #f5e6d3; }
        * { box-sizing: border-box; } body { margin: 0; } main { max-width: 920px; margin: auto; padding: 50px 24px; }
        header { margin-bottom: 32px; } small { color: #d58b55; letter-spacing: 2px; } h1 { font-size: clamp(32px, 6vw, 56px); }
        p { line-height: 1.7; overflow-wrap: anywhere; color: #c9b8a6; } section, form { padding: 24px; margin: 20px 0; border: 1px solid #6a493333; border-radius: 12px; background: #211710; }
        input, select, button { font: inherit; padding: 10px; border-radius: 6px; border: 1px solid #765339; margin: 5px; max-width: 100%; }
        input, select { background: #130e0a; color: #f5e6d3; } button { background: #d5aa78; color: #21130e; cursor: pointer; }
        article { display: flex; flex-wrap: wrap; gap: 16px; justify-content: space-between; align-items: center; padding: 12px 0; border-bottom: 1px solid #6a493333; }
        a { color: #d5aa78; } nav { display: flex; flex-wrap: wrap; gap: 24px; } footer { margin-top: 35px; color: #a99587; font-size: 12px; }
        :focus-visible { outline: 2px solid #f5e6d3; outline-offset: 3px; }
        """;
}
