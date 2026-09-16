<template>
  <AdminLayout>
    <div class="manage-page">
      <div class="toolbar">
        <el-input
          v-model="keyword"
          placeholder="搜索学号 / 姓名"
          clearable
          style="width: 240px"
          @keyup.enter="search"
          @clear="search"
        >
          <template #prefix><el-icon><Search /></el-icon></template>
          <template #append><el-button @click="search">搜索</el-button></template>
        </el-input>
        <el-button @click="loadData"><el-icon><Refresh /></el-icon> 刷新</el-button>
        <div class="toolbar-tip">
          共 {{ total }} 位用户 · 平台余额合计
          <strong class="sum">{{ formatMoney(balanceSum) }}</strong>
        </div>
      </div>

      <el-table :data="users" stripe style="width: 100%" v-loading="loading">
        <el-table-column prop="id" label="ID" width="70" />
        <el-table-column label="学号" width="130">
          <template #default="{ row }">{{ row.username }}</template>
        </el-table-column>
        <el-table-column label="姓名" width="110">
          <template #default="{ row }">{{ row.realName || '-' }}</template>
        </el-table-column>
        <el-table-column label="角色" width="90">
          <template #default="{ row }">
            <el-tag size="small" :type="row.role === 'ADMIN' ? 'danger' : 'primary'" effect="plain">
              {{ row.role === 'ADMIN' ? '管理员' : '学生' }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column label="学院/专业" min-width="180" show-overflow-tooltip>
          <template #default="{ row }">
            {{ [row.college, row.major, row.grade].filter(Boolean).join(' · ') || '-' }}
          </template>
        </el-table-column>
        <el-table-column label="手机号" width="130">
          <template #default="{ row }">
            <span class="masked">{{ row.phone || '-' }}</span>
          </template>
        </el-table-column>
        <el-table-column label="邮箱" width="170" show-overflow-tooltip>
          <template #default="{ row }">
            <span class="masked">{{ row.email || '-' }}</span>
          </template>
        </el-table-column>
        <el-table-column label="余额" width="120" sortable :sort-by="(r) => Number(r.balance)">
          <template #default="{ row }">
            <span class="balance">{{ formatMoney(row.balance) }}</span>
          </template>
        </el-table-column>
        <el-table-column label="状态" width="90">
          <template #default="{ row }">
            <el-tag size="small" :type="row.status === 1 ? 'success' : 'danger'">
              {{ row.status === 1 ? '正常' : '已封禁' }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column label="注册时间" width="160">
          <template #default="{ row }">{{ formatTime(row.createTime) }}</template>
        </el-table-column>
        <el-table-column label="操作" width="200" fixed="right">
          <template #default="{ row }">
            <el-button text type="primary" size="small" @click="openBalance(row)">余额调整</el-button>
            <el-button
              v-if="row.role !== 'ADMIN'"
              text
              :type="row.status === 1 ? 'danger' : 'success'"
              size="small"
              @click="toggleStatus(row)"
            >{{ row.status === 1 ? '封禁' : '解封' }}</el-button>
          </template>
        </el-table-column>
      </el-table>

      <el-empty v-if="!loading && users.length === 0" description="未找到用户" :image-size="80" />

      <div class="pager">
        <el-pagination
          background
          layout="total, prev, pager, next, sizes"
          :total="total"
          :current-page="page"
          :page-size="size"
          :page-sizes="[10, 20, 50]"
          @current-change="onPageChange"
          @size-change="onSizeChange"
        />
      </div>

      <!-- 余额调整 -->
      <el-dialog v-model="balanceVisible" title="余额调整" width="420px">
        <template v-if="current">
          <p class="dialog-tip">
            用户：<strong>{{ current.realName || current.username }}</strong>
            （{{ current.username }}）· 当前余额
            <strong class="balance">{{ formatMoney(current.balance) }}</strong>
          </p>
          <el-form label-width="90px">
            <el-form-item label="调整金额">
              <el-input-number v-model="balanceForm.amount" :step="10" :precision="2" style="width: 100%" />
            </el-form-item>
            <el-form-item label="说明">
              <el-input v-model="balanceForm.remark" maxlength="100" placeholder="选填，如：活动奖励 / 违规扣款" />
            </el-form-item>
          </el-form>
          <el-alert
            type="info"
            :closable="false"
            show-icon
            title="正数为增加余额，负数为扣减余额；调整会写入用户余额流水，且不可超过余额扣减为负。"
          />
        </template>
        <template #footer>
          <el-button @click="balanceVisible = false">取消</el-button>
          <el-button type="primary" :loading="submitting" @click="submitBalance">确认调整</el-button>
        </template>
      </el-dialog>
    </div>
  </AdminLayout>
</template>

<script setup>
import { computed, onMounted, ref } from 'vue'
import AdminLayout from '@/components/AdminLayout.vue'
import { adminGetUsers, adminAdjustBalance, adminUpdateUserStatus } from '@/api'
import { ElMessage, ElMessageBox } from 'element-plus'
import { formatTime, formatMoney } from '@/utils/format'

const keyword = ref('')
const users = ref([])
const total = ref(0)
const page = ref(1)
const size = ref(20)
const loading = ref(false)

const balanceVisible = ref(false)
const submitting = ref(false)
const current = ref(null)
const balanceForm = ref({ amount: 0, remark: '' })

const balanceSum = computed(() =>
  users.value.reduce((sum, u) => sum + Number(u.balance || 0), 0)
)

async function loadData() {
  loading.value = true
  try {
    const res = await adminGetUsers({
      keyword: keyword.value || undefined,
      page: page.value,
      size: size.value
    })
    const data = res.data || {}
    users.value = data.list || []
    total.value = data.total || 0
  } catch {
    /* 拦截器已提示 */
  } finally {
    loading.value = false
  }
}

function search() {
  page.value = 1
  loadData()
}

function onPageChange(p) {
  page.value = p
  loadData()
}

function onSizeChange(s) {
  size.value = s
  page.value = 1
  loadData()
}

function openBalance(row) {
  current.value = row
  balanceForm.value = { amount: 0, remark: '' }
  balanceVisible.value = true
}

async function submitBalance() {
  const amount = Number(balanceForm.value.amount)
  if (!amount) {
    ElMessage.warning('请输入非 0 的调整金额')
    return
  }
  submitting.value = true
  try {
    const res = await adminAdjustBalance(current.value.id, {
      amount,
      remark: balanceForm.value.remark || (amount > 0 ? '管理员增加余额' : '管理员扣减余额')
    })
    ElMessage.success(`调整成功，当前余额 ${formatMoney(res?.data?.balance)}`)
    balanceVisible.value = false
    loadData()
  } catch (e) {
    ElMessage.error(e?.message || '调整失败')
  } finally {
    submitting.value = false
  }
}

async function toggleStatus(row) {
  const toBan = row.status === 1
  try {
    await ElMessageBox.confirm(
      toBan
        ? `确定封禁「${row.realName || row.username}」吗？封禁后该账号将无法登录。`
        : `确定解封「${row.realName || row.username}」吗？`,
      '提示',
      { type: 'warning' }
    )
  } catch {
    return
  }
  try {
    await adminUpdateUserStatus(row.id, { status: toBan ? 0 : 1 })
    ElMessage.success(toBan ? '账号已封禁' : '账号已解封')
    loadData()
  } catch {
    /* 拦截器已提示 */
  }
}

onMounted(loadData)
</script>

<style scoped>
.manage-page {
  display: flex;
  flex-direction: column;
  gap: 16px;
}
.toolbar {
  display: flex;
  gap: 12px;
  align-items: center;
  flex-wrap: wrap;
}
.toolbar-tip {
  margin-left: auto;
  font-size: 13px;
  color: #909399;
}
.sum {
  color: #f56c6c;
}
.masked {
  color: #a8abb2;
  font-size: 13px;
}
.balance {
  color: #f56c6c;
  font-weight: 600;
}
.pager {
  display: flex;
  justify-content: flex-end;
}
.dialog-tip {
  margin: 0 0 14px;
  font-size: 13px;
  color: #606266;
}
</style>
