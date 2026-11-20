(function app() {
  "use strict";

  const key = "shilu.web.v1";
  const members = ["你", "小禾", "阿岑"];
  const sample = {
    trips: [{ id: "lake", title: "南投・湖畔兩日", place: "日月潭，南投", date: "2026-11-14" }],
    records: [{ id: "sample", trip: "lake", title: "清晨的湖岸", date: "2026-11-14", note: "沿著湖岸慢慢走，霧散開後，看見山色倒映在水面。" }],
    expenses: [{ id: "lunch", trip: "lake", title: "湖畔午餐", payer: "你", total: "96000", original: "96000", currency: "TWD", shares: { "你": "32000", "小禾": "32000", "阿岑": "32000" } }],
  };
  const main = document.querySelector("#main");
  const dialog = document.querySelector("#editor");
  const form = document.querySelector("#form");
  const fields = document.querySelector("#fields");
  const error = document.querySelector("#form-error");
  let state = sample;
  let activeEditor;
  let statusTimeout;

  function announce(message) {
    const status = document.querySelector("#status");
    status.textContent = message;
    clearTimeout(statusTimeout);
    statusTimeout = setTimeout(() => { status.textContent = ""; }, 4500);
  }

  try {
    const saved = localStorage.getItem(key);
    if (saved) {
      const parsed = JSON.parse(saved);
      if (!["trips", "records", "expenses"].every(name => Array.isArray(parsed[name]))) throw new Error();
      state = parsed;
    }
  } catch {
    announce("無法讀取本機資料，已顯示範例。");
  }

  const escape = value => String(value).replace(/[&<>"']/g, character => ({
    "&": "&amp;", "<": "&lt;", ">": "&gt;", '"': "&quot;", "'": "&#39;",
  })[character]);
  const tripLink = id => "#trip/" + encodeURIComponent(id);
  const tripName = id => state.trips.find(trip => trip.id === id)?.title ?? "";
  const today = () => {
    const date = new Date();
    return [date.getFullYear(), date.getMonth() + 1, date.getDate()]
      .map(value => String(value).padStart(2, "0")).join("-");
  };

  function money(amount, digits = 2) {
    const value = BigInt(amount);
    const factor = 10n ** BigInt(digits);
    const absolute = value < 0n ? -value : value;
    const whole = (absolute / factor).toLocaleString("zh-TW");
    const fraction = digits ? "." + String(absolute % factor).padStart(digits, "0") : "";
    return `${value < 0n ? "−" : ""}${whole}${fraction}`;
  }

  function parseAmount(text, digits, label) {
    const parts = /^(\d{1,12})(?:\.(\d+))?$/.exec(text);
    if (!parts || (parts[2]?.length ?? 0) > digits) throw new Error(`${label}請填寫有效金額，最多 ${digits} 位小數。`);
    return BigInt(parts[1] + (parts[2] ?? "").padEnd(digits, "0"));
  }

  const add = (type, label, trip = "") =>
    `<button type="button" data-edit="${type}" data-trip="${escape(trip)}">${label}</button>`;
  const empty = message => `<p class="empty muted">${message}</p>`;
  const heading = (title, subtitle, action = "") => `<header class="page-head section-head">
    <div><h1>${escape(title)}</h1><p class="muted">${escape(subtitle)}</p></div>${action}</header>`;

  function tripCards(trips) {
    return trips.length ? `<div class="grid">${trips.map(trip => `
      <a class="card card-link" href="${tripLink(trip.id)}">
        <span class="badge">${escape(trip.date)}</span>
        <h3 class="trip-title">${escape(trip.title)}</h3>
        <p>${escape(trip.place || "旅程")}</p>
        <p>${state.records.filter(record => record.trip === trip.id).length} 則紀錄　→</p>
      </a>`).join("")}</div>` : empty("建立一趟旅程，開始整理沿途的紀錄。");
  }

  function records(entries) {
    return entries.length ? `<div class="entries">${entries.map(record => `
      <article class="entry">
        <div class="section-head"><h3>${escape(record.title)}</h3><span class="badge">${escape(record.date)}</span></div>
        ${record.note ? `<p class="note">${escape(record.note)}</p>` : ""}
        <p>${record.trip ? escape(tripName(record.trip)) : "私人日誌"}</p>
      </article>`).join("")}</div>` : empty("這裡還沒有紀錄。");
  }

  function ledger(trip) {
    const entries = state.expenses.filter(expense => expense.trip === trip.id);
    const balance = Object.fromEntries(members.map(name => [name, 0n]));
    let total = 0n;
    for (const expense of entries) {
      total += BigInt(expense.total);
      balance[expense.payer] += BigInt(expense.total);
      for (const [name, share] of Object.entries(expense.shares)) balance[name] -= BigInt(share);
    }
    return `<section>
      <div class="section-head"><h2>旅費分帳</h2>${add("expense", "＋ 新增支出", trip.id)}</div>
      <div class="grid">${members.map(name => `<article class="card"><h3>${name}</h3><strong>${money(balance[name])}</strong>
        <p>${balance[name] > 0n ? "應收" : balance[name] < 0n ? "應付" : "已結清"} · TWD</p></article>`).join("")}</div>
    </section><section>
      <div class="section-head"><h2>支出明細</h2><span class="badge">總額 ${money(total)} TWD</span></div>
      ${entries.length ? `<div class="entries">${entries.map(expense => `<article class="entry">
        <div class="section-head"><h3>${escape(expense.title)}</h3><strong>${money(expense.total)} TWD</strong></div>
        <p>${escape(expense.payer)}付款 · ${money(expense.original, expense.currency === "JPY" ? 0 : 2)} ${expense.currency}</p>
        <p>${Object.entries(expense.shares).map(([name, amount]) => `${escape(name)} ${money(amount)}`).join("　")}</p>
      </article>`).join("")}</div>` : empty("這趟旅程還沒有支出。")}
    </section>`;
  }

  function render() {
    const route = location.hash.slice(1) || "home";
    const trip = state.trips.find(item => tripLink(item.id) === location.hash);
    const tab = trip ? "trips" : ["trips", "diary", "expenses"].includes(route) ? route : "home";
    for (const link of document.querySelectorAll("[data-nav]")) {
      if (link.dataset.nav === tab) link.setAttribute("aria-current", "page");
      else link.removeAttribute("aria-current");
    }
    if (trip) {
      main.innerHTML = `<a class="back" href="#trips">← 我的旅程</a>
        ${heading(trip.title, [trip.date, trip.place].filter(Boolean).join(" · "), add("record", "＋ 新增旅程紀錄", trip.id))}
        <section><div class="section-head"><h2>沿途紀錄</h2></div>
        ${records(state.records.filter(record => record.trip === trip.id))}</section>${ledger(trip)}`;
    } else if (tab === "trips") {
      main.innerHTML = heading("我的旅程", "把沿途的片刻留在同一趟旅程。", add("trip", "＋ 建立旅程")) + tripCards(state.trips);
    } else if (tab === "diary") {
      main.innerHTML = heading("私人日誌", "只保存在這個瀏覽器裡的日常紀錄。", add("record", "＋ 新增日誌")) +
        records(state.records.filter(record => !record.trip));
    } else if (tab === "expenses") {
      main.innerHTML = heading("旅費分帳", "選擇一趟旅程，查看支出與每人的應收應付。") +
        `<div class="entries">${state.trips.map(item => `<a class="card card-link" href="${tripLink(item.id)}">
          <div class="section-head"><h3>${escape(item.title)}</h3><span class="badge">${state.expenses.filter(expense => expense.trip === item.id).length} 筆支出 →</span></div></a>`).join("")}</div>`;
    } else {
      main.innerHTML = `<div class="hero"><div class="hero-content">
        <p>旅行與日常筆記</p><h1>日子，都值得記下</h1>
        <p>收藏旅途風景，留下日常片刻，整理一起花的旅費。</p>
        <a class="button" href="${state.trips.length ? tripLink(state.trips[0].id) : "#trips"}">探索範例旅程 →</a>
      </div></div>
      <section><div class="section-head"><h2>我的旅程</h2>${add("trip", "＋ 建立旅程")}</div>${tripCards(state.trips.slice(0, 3))}</section>
      <section><div class="section-head"><h2>最近紀錄</h2>${add("record", "＋ 隨手記")}</div>${records(state.records.slice(0, 3))}</section>`;
    }
  }

  function showEditor(type, trip) {
    activeEditor = { type, trip };
    form.reset();
    error.textContent = "";
    const title = { trip: "建立旅程", record: trip ? "新增旅程紀錄" : "新增私人日誌", expense: "新增支出" };
    document.querySelector("#editor-title").textContent = title[type];
    const name = `<label>${type === "expense" ? "支出名稱" : "標題"}<input name="title" required maxlength="80" autofocus></label>`;
    const date = `<label>日期<input name="date" type="date" required value="${today()}"></label>`;
    if (type === "trip") {
      fields.innerHTML = name + date + `<label>地點<input name="place" maxlength="80"></label>`;
    } else if (type === "record") {
      fields.innerHTML = name + date + `<label>內容<textarea name="note" rows="4" maxlength="2000"></textarea></label>`;
    } else {
      fields.innerHTML = name + `<div class="form-grid">
        <label>原始金額<input name="amount" required inputmode="decimal" placeholder="0.00"></label>
        <label>幣別<select name="currency"><option>TWD</option><option>JPY</option><option>USD</option></select></label>
      </div>
      <label>匯率（1 原幣等於多少 TWD）<input name="rate" value="1" inputmode="decimal" disabled required></label>
      <label>付款人<select name="payer">${members.map(member => `<option>${member}</option>`).join("")}</select></label>
      <label>分攤方式<select name="split"><option value="equal">三人平均分攤</option><option value="custom">指定每人金額</option></select></label>
      <div id="shares" hidden>${members.map((member, index) => `<label>${member}（TWD）<input name="share${index}" inputmode="decimal" value="0"></label>`).join("")}</div>`;
    }
    dialog.showModal();
  }

  function buildExpense(data, base) {
    const currency = data.get("currency");
    const digits = currency === "JPY" ? 0 : 2;
    const original = parseAmount(data.get("amount").trim(), digits, "原始金額");
    if (original === 0n) throw new Error("支出金額須大於零。");
    const rate = currency === "TWD" ? 1000000000000n : parseAmount(data.get("rate").trim(), 12, "匯率");
    if (rate === 0n) throw new Error("匯率須大於零。");
    const divisor = 10n ** BigInt(12 + digits);
    const total = (original * rate * 100n + divisor / 2n) / divisor;
    const count = BigInt(members.length);
    const custom = data.get("split") === "custom";
    const amounts = members.map((member, index) => custom ?
      parseAmount(data.get(`share${index}`).trim(), 2, member) :
      total / count + (BigInt(index) < total % count ? 1n : 0n));
    if (amounts.reduce((sum, amount) => sum + amount, 0n) !== total) {
      throw new Error(`指定分攤合計必須等於 ${money(total)} TWD。`);
    }
    return { ...base, currency, payer: data.get("payer"), total: String(total), original: String(original),
      shares: Object.fromEntries(members.map((name, index) => [name, String(amounts[index])])) };
  }

  main.addEventListener("click", event => {
    const button = event.target.closest("[data-edit]");
    if (button) showEditor(button.dataset.edit, button.dataset.trip);
  });
  dialog.addEventListener("click", event => {
    if (event.target.closest("[data-close]")) dialog.close();
  });
  form.addEventListener("change", event => {
    if (event.target.name === "currency") {
      const rate = form.elements.rate;
      rate.disabled = event.target.value === "TWD";
      rate.value = rate.disabled ? "1" : "";
    }
    if (event.target.name === "split") {
      document.querySelector("#shares").hidden = event.target.value !== "custom";
    }
  });
  form.addEventListener("submit", event => {
    event.preventDefault();
    const data = new FormData(form);
    const { type, trip } = activeEditor;
    const title = data.get("title").trim();
    try {
      if (!title) throw new Error("請填寫標題。");
      const id = globalThis.crypto?.randomUUID?.() ?? `${Date.now()}-${Math.random().toString(36).slice(2)}`;
      const base = { id, title };
      const next = { ...state };
      if (type === "trip") next.trips = [...state.trips, { ...base, place: data.get("place").trim(), date: data.get("date") }];
      if (type === "record") next.records = [{ ...base, trip, date: data.get("date"), note: data.get("note").trim() }, ...state.records];
      if (type === "expense") next.expenses = [buildExpense(data, { ...base, trip }), ...state.expenses];
      try {
        localStorage.setItem(key, JSON.stringify(next));
      } catch {
        throw new Error("無法寫入瀏覽器儲存空間，請確認瀏覽器允許本機儲存。");
      }
      state = next;
      dialog.close();
      if (type === "trip") location.hash = tripLink(id);
      else if (type === "record" && !trip) location.hash = "#diary";
      render();
      announce("已儲存");
    } catch (problem) {
      error.textContent = problem.message;
    }
  });

  window.addEventListener("hashchange", render);
  render();
})();
