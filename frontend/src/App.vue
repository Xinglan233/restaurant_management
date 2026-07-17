<template>
  <div v-if="!user" class="login-page">
    <form class="login-box" @submit.prevent="login">
      <h1>饭店点餐管理系统</h1>
      <label>用户名<input v-model="loginForm.username" /></label>
      <label>密码<input v-model="loginForm.password" type="password" /></label>
      <button>登录</button>
      <p class="tip">默认账号：admin / 123456</p>
    </form>
  </div>

  <div v-else class="app">
    <aside>
      <h2>点餐系统</h2>
      <p>{{ user.real_name }}（{{ displayRole }}）</p>
      <button v-for="item in visibleMenus" :key="item.key" :class="{ active: page === item.key }" @click="page = item.key">
        {{ item.name }}
      </button>
      <button @click="logout">退出登录</button>
    </aside>

    <main>
      <header>
        <h1>{{ title }}</h1>
        <button @click="loadAll">刷新</button>
      </header>

      <section v-if="page === 'home'" class="grid">
        <div class="card"><b>餐桌总数</b><span>{{ dashboard.tableCount || 0 }}</span></div>
        <div class="card"><b>空闲餐桌</b><span>{{ dashboard.emptyTableCount || 0 }}</span></div>
        <div class="card"><b>等待人数</b><span>{{ dashboard.waitingCount || 0 }}</span></div>
        <div v-if="isManager" class="card"><b>今日营收</b><span>￥{{ money(dashboard.todayRevenue) }}</span></div>
        <div class="panel wide">
          <h3>热门菜品</h3>
          <table><tbody><tr v-for="d in dashboard.hotDishes" :key="d.name"><td>{{ d.name }}</td><td>￥{{ money(d.price) }}</td></tr></tbody></table>
        </div>
        <div v-if="!isManager" class="panel wide">
          <h3>服务员工作台</h3>
          <p class="tip">服务员主要负责叫号排队、顾客点餐和订单结算。餐桌、菜单、VIP、员工和报表由店长管理。</p>
        </div>
      </section>

      <section v-if="page === 'queue'" class="panel">
        <div class="section-title">
          <h3>叫号排队</h3>
          <div>
            <button @click="openModal('queue')">顾客取号</button>
            <button @click="callNext">叫下一号</button>
          </div>
        </div>
        <table>
          <thead><tr><th>号码</th><th>人数</th><th>状态</th><th>取号时间</th><th>安排桌号</th><th>操作</th></tr></thead>
          <tbody>
            <tr v-for="q in queues" :key="q.id">
              <td>{{ q.queue_no }}</td><td>{{ q.people_count }}</td><td>{{ q.status }}</td><td>{{ q.create_time }}</td>
              <td>
                <select v-if="q.status === '已叫号'" v-model="q.table_id">
                  <option value="">选择空桌</option>
                  <option v-for="t in emptyTables" :key="t.id" :value="t.id">{{ t.table_no }}（{{ t.seats }}人）</option>
                </select>
                <span v-else>{{ q.status === '已安排' ? '已安排' : '需先叫号' }}</span>
              </td>
              <td>
                <button v-if="q.status === '已叫号'" @click="assignQueue(q)">安排</button>
                <button v-if="q.status !== '已取消'" @click="cancelQueue(q)">取消</button>
                <span v-if="q.status === '已取消'">已取消，需重新取号</span>
              </td>
            </tr>
          </tbody>
        </table>
      </section>

      <section v-if="page === 'tables'" class="panel">
        <div class="section-title">
          <h3>餐桌管理</h3>
          <button @click="openTableAdd">新增餐桌</button>
        </div>
        <table><thead><tr><th>餐桌号</th><th>座位数</th><th>状态</th><th>操作</th></tr></thead><tbody>
          <tr v-for="t in tables" :key="t.id"><td>{{ t.table_no }}</td><td>{{ t.seats }}</td><td>{{ t.status }}</td><td><button @click="editTable(t)">编辑</button><button @click="remove('tables', t.id)">删除</button></td></tr>
        </tbody></table>
      </section>

      <section v-if="page === 'dishes'" class="panel">
        <div class="section-title">
          <h3>菜单管理</h3>
          <div>
            <button @click="openDishAdd">新增菜品</button>
            <button @click="openModal('category')">新增分类</button>
          </div>
        </div>
        <table><thead><tr><th>编号</th><th>菜名</th><th>分类</th><th>价格</th><th>标注</th><th>状态</th><th>操作</th></tr></thead><tbody>
          <tr v-for="d in dishes" :key="d.id"><td>{{ d.dish_no }}</td><td>{{ d.name }}</td><td>{{ d.category_name }}</td><td>￥{{ money(d.price) }}</td><td>{{ d.is_hot ? '热门' : '' }}</td><td>{{ d.is_on_sale ? '上架' : '下架' }}</td><td><button @click="editDish(d)">编辑</button><button @click="remove('dishes', d.id)">删除</button></td></tr>
        </tbody></table>
      </section>

      <section v-if="page === 'vips'" class="panel">
        <div class="section-title">
          <h3>VIP 顾客管理</h3>
          <button @click="openVipAdd">新增VIP</button>
        </div>
        <table><thead><tr><th>姓名</th><th>电话</th><th>等级</th><th>折扣</th><th>积分</th><th>操作</th></tr></thead><tbody>
          <tr v-for="v in vips" :key="v.id"><td>{{ v.name }}</td><td>{{ v.phone }}</td><td>{{ v.level_name }}</td><td>{{ v.discount }}</td><td>{{ v.points }}</td><td><button @click="editVip(v)">编辑</button><button @click="remove('vips', v.id)">删除</button></td></tr>
        </tbody></table>
      </section>

      <section v-if="page === 'staff'" class="panel">
        <div class="section-title">
          <h3>服务员管理</h3>
          <button @click="openStaffAdd">新增员工</button>
        </div>
        <table><thead><tr><th>用户名</th><th>姓名</th><th>角色</th><th>操作</th></tr></thead><tbody>
          <tr v-for="s in staff" :key="s.id"><td>{{ s.username }}</td><td>{{ s.real_name }}</td><td>{{ s.role }}</td><td><button @click="editStaff(s)">编辑</button><button @click="remove('staff', s.id)">删除</button></td></tr>
        </tbody></table>
      </section>

      <section v-if="page === 'order'" class="panel">
        <h3>顾客点餐</h3>
        <form class="row-form" @submit.prevent="createOrder">
          <select v-model.number="orderForm.table_id"><option value="">选择餐桌</option><option v-for="t in orderTables" :key="t.id" :value="t.id">{{ t.table_no }}（{{ t.seats }}人，{{ t.status }}）</option></select>
          <button>下单</button>
        </form>
        <div class="dish-list">
          <label v-for="d in onSaleDishes" :key="d.id" class="dish-card">
            <img :src="d.image_url" alt="" />
            <b>{{ d.name }} <em v-if="d.is_hot">热门</em></b>
            <span>￥{{ money(d.price) }}</span>
            <input v-model.number="cart[d.id]" type="number" min="0" placeholder="数量" />
          </label>
        </div>
      </section>

      <section v-if="page === 'orders'" class="panel">
        <h3>订单查询与结算</h3>
        <table><thead><tr><th>订单号</th><th>桌号</th><th>顾客</th><th>时间</th><th>总价</th><th>实付</th><th>状态</th><th>操作</th></tr></thead><tbody>
          <tr v-for="o in orders" :key="o.id">
            <td>{{ o.order_no }}</td><td>{{ o.table_no }}</td><td>{{ o.customer_name }}</td><td>{{ o.order_time }}</td><td>￥{{ money(o.total_amount) }}</td><td>￥{{ money(o.pay_amount) }}</td><td>{{ o.status }}</td>
            <td><button @click="showOrder(o.id)">查看</button></td>
          </tr>
        </tbody></table>
        <div v-if="currentOrder.order" class="detail-box">
          <h3>订单 {{ currentOrder.order.order_no }}</h3>
          <table><thead><tr><th>菜名</th><th>单价</th><th>数量</th><th>小计</th></tr></thead><tbody><tr v-for="i in currentOrder.items" :key="i.id"><td>{{ i.dish_name }}</td><td>￥{{ money(i.price) }}</td><td>{{ i.quantity }}</td><td>￥{{ money(i.subtotal) }}</td></tr></tbody></table>
          <form v-if="currentOrder.order.status === '用餐中'" class="row-form" @submit.prevent="appendDish">
            <select v-model.number="appendForm.dish_id"><option value="">追加菜品</option><option v-for="d in onSaleDishes" :key="d.id" :value="d.id">{{ d.name }}</option></select>
            <input v-model.number="appendForm.quantity" type="number" min="1" placeholder="数量" />
            <button>追加</button>
          </form>
          <form v-if="currentOrder.order.status === '用餐中'" class="row-form" @submit.prevent="checkout">
            <select v-model.number="checkoutForm.vip_id"><option value="">临时顾客（无折扣）</option><option v-for="v in vips" :key="v.id" :value="v.id">{{ v.name }} {{ v.level_name }} {{ v.discount }}</option></select>
            <button>结算</button>
          </form>
          <p v-else class="tip">该订单已结账，只能查看明细。</p>
        </div>
      </section>

      <section v-if="page === 'reports'" class="panel">
        <h3>统计报表</h3>
        <form class="row-form" @submit.prevent="loadReports">
          <input v-model="reportForm.start_time" type="datetime-local" />
          <input v-model="reportForm.end_time" type="datetime-local" />
          <button>查询统计</button>
          <button type="button" @click="markHot">标注热门菜</button>
        </form>
        <h3>营收汇总：订单 {{ revenue.summary?.order_count || 0 }} 单，总价 ￥{{ money(revenue.summary?.total_amount) }}，实收 ￥{{ money(revenue.summary?.pay_amount) }}</h3>
        <table><thead><tr><th>订单号</th><th>桌号</th><th>顾客</th><th>时间</th><th>总价</th><th>折扣</th><th>实收</th></tr></thead><tbody><tr v-for="r in revenue.details" :key="r.order_no"><td>{{ r.order_no }}</td><td>{{ r.table_no }}</td><td>{{ r.customer_name }}</td><td>{{ r.order_time }}</td><td>{{ money(r.total_amount) }}</td><td>{{ r.discount }}</td><td>{{ money(r.pay_amount) }}</td></tr></tbody></table>
        <h3>菜品销量排行</h3>
        <table><thead><tr><th>菜品</th><th>分类</th><th>销量</th><th>销售额</th><th>标注</th></tr></thead><tbody><tr v-for="s in sales" :key="s.dish_id"><td>{{ s.name }}</td><td>{{ s.category_name }}</td><td>{{ s.sale_count }}</td><td>￥{{ money(s.sale_amount) }}</td><td>{{ s.is_hot ? '热门' : '' }}</td></tr></tbody></table>
      </section>
    </main>
  </div>

  <div v-if="modal" class="modal-mask" @click.self="closeModal">
    <form class="modal-box" @submit.prevent="submitModal">
      <h3>{{ modalTitle }}</h3>

      <template v-if="modal === 'queue'">
        <label>用餐人数<input v-model.number="queueForm.people_count" type="number" min="1" /></label>
      </template>

      <template v-if="modal === 'table'">
        <p class="tip">餐桌号按座位数自动生成：2人桌 Axx，4人桌 Bxx，6人及以上 Cxx。</p>
        <label>座位数<input v-model.number="tableForm.seats" type="number" min="1" /></label>
        <label>状态<select v-model="tableForm.status"><option>空</option><option>占用</option></select></label>
      </template>

      <template v-if="modal === 'dish'">
        <p v-if="!dishForm.id" class="tip">菜品编号由系统自动生成。</p>
        <label v-if="dishForm.id">菜品编号<input v-model="dishForm.dish_no" /></label>
        <label>菜名<input v-model="dishForm.name" /></label>
        <label>分类<select v-model.number="dishForm.category_id"><option value="">分类</option><option v-for="c in categories" :key="c.id" :value="c.id">{{ c.name }}</option></select></label>
        <label>价格<input v-model.number="dishForm.price" type="number" min="0" step="0.01" /></label>
        <label>图片地址<input v-model="dishForm.image_url" /></label>
        <label class="check"><input v-model="dishForm.is_hot" type="checkbox" />热门</label>
        <label class="check"><input v-model="dishForm.is_on_sale" type="checkbox" />上架</label>
      </template>

      <template v-if="modal === 'category'">
        <label>分类名称<input v-model="categoryName" /></label>
      </template>

      <template v-if="modal === 'vip'">
        <label>姓名<input v-model="vipForm.name" /></label>
        <label>手机号<input v-model="vipForm.phone" /></label>
        <label>等级<select v-model="vipForm.level_name"><option>普通VIP</option><option>银卡VIP</option><option>金卡VIP</option></select></label>
        <label>折扣<input v-model.number="vipForm.discount" type="number" min="0.1" max="1" step="0.01" /></label>
        <label>积分<input v-model.number="vipForm.points" type="number" min="0" /></label>
      </template>

      <template v-if="modal === 'staff'">
        <label>用户名<input v-model="staffForm.username" /></label>
        <label>密码<input v-model="staffForm.password" type="password" placeholder="不改密码可留空" /></label>
        <label>姓名<input v-model="staffForm.real_name" /></label>
        <label>角色<select v-model="staffForm.role"><option>服务员</option><option>店长</option></select></label>
      </template>

      <div class="modal-actions">
        <button>保存</button>
        <button type="button" class="secondary" @click="closeModal">取消</button>
      </div>
    </form>
  </div>

  <div v-if="message" class="message" @click="message=''">{{ message }}</div>
</template>

<script setup>
import { computed, onMounted, reactive, ref } from 'vue'

const API = 'http://localhost:8080/api'
const user = ref(JSON.parse(localStorage.getItem('user') || 'null'))
const page = ref('home')
const message = ref('')
const modal = ref('')
const menus = [
  { key: 'home', name: '首页', roles: ['店长', '管理员', '服务员'] },
  { key: 'queue', name: '叫号排队', roles: ['店长', '管理员', '服务员'] },
  { key: 'order', name: '顾客点餐', roles: ['店长', '管理员', '服务员'] },
  { key: 'orders', name: '订单结算', roles: ['店长', '管理员', '服务员'] },
  { key: 'tables', name: '餐桌管理', roles: ['店长', '管理员'] },
  { key: 'dishes', name: '菜单管理', roles: ['店长', '管理员'] },
  { key: 'vips', name: 'VIP管理', roles: ['店长', '管理员'] },
  { key: 'staff', name: '服务员管理', roles: ['店长', '管理员'] },
  { key: 'reports', name: '统计报表', roles: ['店长', '管理员'] }
]
const isManager = computed(() => user.value?.role === '店长' || user.value?.role === '管理员')
const displayRole = computed(() => user.value?.role === '管理员' ? '店长' : user.value?.role)
const visibleMenus = computed(() => menus.filter(m => m.roles.includes(user.value?.role)))
const title = computed(() => menus.find(m => m.key === page.value)?.name)
const modalTitle = computed(() => ({
  queue: '顾客取号',
  table: tableForm.id ? '编辑餐桌' : '新增餐桌',
  dish: dishForm.id ? '编辑菜品' : '新增菜品',
  category: '新增分类',
  vip: vipForm.id ? '编辑VIP' : '新增VIP',
  staff: staffForm.id ? '编辑员工' : '新增员工'
}[modal.value] || '操作'))

const loginForm = reactive({ username: 'admin', password: '123456' })
const dashboard = ref({})
const tables = ref([])
const categories = ref([])
const dishes = ref([])
const vips = ref([])
const staff = ref([])
const queues = ref([])
const orders = ref([])
const revenue = ref({ details: [], summary: {} })
const sales = ref([])
const currentOrder = ref({})
const cart = reactive({})
const tableForm = reactive({ id: '', table_no: '', seats: 2, status: '空' })
const dishForm = reactive({ id: '', dish_no: '', name: '', category_id: '', price: 0, image_url: '', is_hot: false, is_on_sale: true })
const vipForm = reactive({ id: '', name: '', phone: '', level_name: '普通VIP', discount: 0.95, points: 0 })
const staffForm = reactive({ id: '', username: '', password: '', real_name: '', role: '服务员' })
const queueForm = reactive({ people_count: 2 })
const orderForm = reactive({ table_id: '' })
const appendForm = reactive({ dish_id: '', quantity: 1 })
const checkoutForm = reactive({ vip_id: '' })
const reportForm = reactive({ start_time: todayStart(), end_time: todayEnd() })
const categoryName = ref('')

const emptyTables = computed(() => tables.value.filter(t => t.status === '空'))
const assignedTableIds = computed(() => queues.value.filter(q => q.status === '已安排' && q.table_id).map(q => Number(q.table_id)))
const orderTables = computed(() => tables.value.filter(t => t.status === '空' || assignedTableIds.value.includes(Number(t.id))))
const onSaleDishes = computed(() => dishes.value.filter(d => Number(d.is_on_sale) === 1))

async function request(path, options = {}) {
  const headers = { 'Content-Type': 'application/json', ...(options.headers || {}) }
  const token = localStorage.getItem('token')
  if (token) headers.Authorization = `Bearer ${token}`
  const res = await fetch(API + path, { ...options, headers })
  const data = await res.json()
  if (data.success === false) throw new Error(data.message)
  return data
}
async function login() {
  try {
    const data = await request('/login', { method: 'POST', body: JSON.stringify(loginForm) })
    user.value = data.user
    localStorage.setItem('user', JSON.stringify(data.user))
    localStorage.setItem('token', data.token)
    page.value = 'home'
    await loadAll()
  } catch (e) { show(e.message) }
}
function logout() { localStorage.removeItem('user'); localStorage.removeItem('token'); user.value = null }
async function loadAll() {
  if (!user.value) return
  if (!visibleMenus.value.some(m => m.key === page.value)) page.value = 'home'
  try {
    dashboard.value = await request('/dashboard')
    tables.value = await request('/tables')
    categories.value = await request('/categories')
    dishes.value = await request('/dishes')
    vips.value = await request('/vips')
    queues.value = await request('/queue')
    orders.value = await request('/orders')
    if (isManager.value) staff.value = await request('/staff')
  } catch (e) { show(e.message) }
}
async function saveTable() { await save('/tables', tableForm, { id: '', table_no: '', seats: 2, status: '空' }) }
async function saveDish() { await save('/dishes', dishForm, { id: '', dish_no: '', name: '', category_id: '', price: 0, image_url: '', is_hot: false, is_on_sale: true }) }
async function saveVip() { await save('/vips', vipForm, { id: '', name: '', phone: '', level_name: '普通VIP', discount: 0.95, points: 0 }) }
async function saveStaff() { await save('/staff', staffForm, { id: '', username: '', password: '', real_name: '', role: '服务员' }) }
async function save(path, form, empty) {
  try {
    const id = form.id
    await request(id ? `${path}/${id}` : path, { method: id ? 'PUT' : 'POST', body: JSON.stringify(form) })
    Object.assign(form, empty)
    closeModal()
    show('保存成功')
    await loadAll()
  } catch (e) { show(e.message) }
}
function editTable(t) { Object.assign(tableForm, t); openModal('table') }
function editDish(d) { Object.assign(dishForm, { ...d, is_hot: !!d.is_hot, is_on_sale: !!d.is_on_sale }); openModal('dish') }
function editVip(v) { Object.assign(vipForm, v); openModal('vip') }
function editStaff(s) { Object.assign(staffForm, { ...s, password: '' }); openModal('staff') }
async function remove(path, id) {
  if (!confirm('确认删除这条数据吗？')) return
  try { await request(`/${path}/${id}`, { method: 'DELETE' }); show('删除成功'); await loadAll() } catch (e) { show(e.message) }
}
async function addCategory() {
  try { await request('/categories', { method: 'POST', body: JSON.stringify({ name: categoryName.value }) }); categoryName.value = ''; closeModal(); await loadAll() } catch (e) { show(e.message) }
}
async function takeNumber() { try { const d = await request('/queue/take', { method: 'POST', body: JSON.stringify(queueForm) }); show(`取号成功：${d.queue_no}`); await loadAll() } catch (e) { show(e.message) } }
async function callNext() {
  try {
    const d = await request('/queue/call-next', { method: 'POST' })
    await loadAll()
    const called = queues.value.find(q => Number(q.id) === Number(d.queue.id))
    if (called) {
      const table = findBestTable(called.people_count)
      if (table) called.table_id = table.id
    }
    show('叫号成功')
  } catch (e) { show(e.message) }
}
async function assignQueue(q) { try { await request(`/queue/${q.id}/assign`, { method: 'POST', body: JSON.stringify({ table_id: q.table_id }) }); await loadAll() } catch (e) { show(e.message) } }
async function cancelQueue(q) {
  if (!confirm(`确定取消排队号 ${q.queue_no} 吗？取消后不能再安排桌子，必须重新取号。`)) return
  try { await request(`/queue/${q.id}/cancel`, { method: 'POST' }); show('已取消，请重新取号'); await loadAll() } catch (e) { show(e.message) }
}
async function createOrder() {
  const items = Object.entries(cart).filter(([, n]) => Number(n) > 0).map(([dish_id, quantity]) => ({ dish_id, quantity }))
  try {
    const d = await request('/orders', { method: 'POST', body: JSON.stringify({ table_id: orderForm.table_id, items }) })
    show(`下单成功：${d.order_no}`)
    Object.keys(cart).forEach(k => delete cart[k])
    orderForm.table_id = ''
    await loadAll()
  } catch (e) { show(e.message) }
}
async function showOrder(id) { currentOrder.value = await request(`/orders/${id}`) }
async function appendDish() {
  try { await request(`/orders/${currentOrder.value.order.id}/items`, { method: 'POST', body: JSON.stringify(appendForm) }); appendForm.dish_id = ''; appendForm.quantity = 1; await showOrder(currentOrder.value.order.id); await loadAll() } catch (e) { show(e.message) }
}
async function checkout() {
  try { const d = await request(`/orders/${currentOrder.value.order.id}/checkout`, { method: 'POST', body: JSON.stringify(checkoutForm) }); show(`结账成功，实收￥${money(d.pay_amount)}`); currentOrder.value = {}; await loadAll() } catch (e) { show(e.message) }
}
async function loadReports() {
  try {
    const body = { start_time: fixTime(reportForm.start_time), end_time: fixTime(reportForm.end_time) }
    revenue.value = await request('/reports/revenue', { method: 'POST', body: JSON.stringify(body) })
    sales.value = await request('/reports/dish-sales', { method: 'POST', body: JSON.stringify(body) })
  } catch (e) { show(e.message) }
}
async function markHot() { try { await request('/reports/mark-hot', { method: 'POST', body: JSON.stringify({ top: 3 }) }); show('已按销量标注热门菜'); await loadAll(); await loadReports() } catch (e) { show(e.message) } }
function openModal(name) { modal.value = name }
function closeModal() { modal.value = '' }
function openTableAdd() {
  Object.assign(tableForm, { id: '', table_no: '', seats: 2, status: '空' })
  openModal('table')
}
function openDishAdd() {
  Object.assign(dishForm, { id: '', dish_no: '', name: '', category_id: '', price: 0, image_url: '', is_hot: false, is_on_sale: true })
  openModal('dish')
}
function openVipAdd() {
  Object.assign(vipForm, { id: '', name: '', phone: '', level_name: '普通VIP', discount: 0.95, points: 0 })
  openModal('vip')
}
function openStaffAdd() {
  Object.assign(staffForm, { id: '', username: '', password: '123456', real_name: '', role: '服务员' })
  openModal('staff')
}
async function submitModal() {
  if (modal.value === 'queue') {
    await takeNumber()
    closeModal()
  } else if (modal.value === 'table') {
    await saveTable()
  } else if (modal.value === 'dish') {
    await saveDish()
  } else if (modal.value === 'category') {
    await addCategory()
  } else if (modal.value === 'vip') {
    await saveVip()
  } else if (modal.value === 'staff') {
    await saveStaff()
  }
}
function show(text) { message.value = text; setTimeout(() => message.value = '', 2500) }
function money(v) { return Number(v || 0).toFixed(2) }
function fixTime(v) { return v ? v.replace('T', ' ') + ':00' : '' }
function findBestTable(peopleCount) {
  return [...emptyTables.value]
    .filter(t => Number(t.seats) >= Number(peopleCount))
    .sort((a, b) => Number(a.seats) - Number(b.seats) || String(a.table_no).localeCompare(String(b.table_no)))[0]
}
function todayStart() { return new Date().toISOString().slice(0, 10) + 'T00:00' }
function todayEnd() { return new Date().toISOString().slice(0, 10) + 'T23:59' }

onMounted(async () => { await loadAll(); if (isManager.value) await loadReports() })
</script>
