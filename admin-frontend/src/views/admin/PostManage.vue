<template>
  <AdminLayout>
    <div class="manage-page">
      <div class="toolbar">
        <el-radio-group v-model="statusFilter" @change="loadData">
          <el-radio-button value="">全部</el-radio-button>
          <el-radio-button value="PUBLISHED">已发布</el-radio-button>
          <el-radio-button value="DELETED">已删除</el-radio-button>
        </el-radio-group>
        <el-input
          v-model="keyword"
          placeholder="搜索帖子标题 / 内容"
          clearable
          style="width: 260px"
          @keyup.enter="loadData"
          @clear="loadData"
        >
          <template #prefix><el-icon><Search /></el-icon></template>
          <template #append><el-button @click="loadData">搜索</el-button></template>
        </el-input>
        <el-button @click="loadData"><el-icon><Refresh /></el-icon> 刷新</el-button>
        <el-button type="primary" @click="openCategoryManage"><el-icon><Menu /></el-icon> 分类管理</el-button>
      </div>

      <el-table :data="posts" stripe style="width: 100%">
        <el-table-column prop="title" label="标题" min-width="180" show-overflow-tooltip />
        <el-table-column prop="content" label="内容" min-width="260" show-overflow-tooltip />
        <el-table-column label="分类" width="110">
          <template #default="{ row }">
            <el-tag size="small" effect="plain">{{ categoryText(row.category) }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column label="作者" width="130" show-overflow-tooltip>
          <template #default="{ row }">
            {{ row.author?.displayName || ('用户 #' + row.userId) }}
          </template>
        </el-table-column>
        <el-table-column label="回复/点赞" width="110">
          <template #default="{ row }">{{ row.replyCount || 0 }} / {{ row.likeCount || 0 }}</template>
        </el-table-column>
        <el-table-column label="发布时间" width="160">
          <template #default="{ row }">{{ formatTime(row.createTime) }}</template>
        </el-table-column>
        <el-table-column label="操作" width="170" fixed="right">
          <template #default="{ row }">
            <el-button text type="primary" size="small" @click="openDetail(row)">查看/回复</el-button>
            <el-button text type="danger" size="small" @click="removePost(row)">删除</el-button>
          </template>
        </el-table-column>
      </el-table>
      <el-empty v-if="posts.length === 0" description="暂无帖子" :image-size="80" />

      <div style="display: flex; justify-content: flex-end; margin-top: 16px">
        <el-pagination
          v-model:current-page="page"
          v-model:page-size="size"
          :total="total"
          :page-sizes="[10, 20, 50]"
          layout="total, sizes, prev, pager, next"
          @current-change="loadData"
          @size-change="onSizeChange"
        />
      </div>

      <!-- 帖子详情 + 回复管理 -->
      <el-dialog v-model="detailVisible" title="帖子详情与回复管理" width="720px">
        <div v-loading="detailLoading">
          <template v-if="current">
            <el-descriptions :column="2" border>
              <el-descriptions-item label="标题" :span="2">{{ current.title }}</el-descriptions-item>
              <el-descriptions-item label="分类">{{ categoryText(current.category) }}</el-descriptions-item>
              <el-descriptions-item label="作者">
                {{ current.author?.displayName || ('用户 #' + current.userId) }}
                <span v-if="current.author?.college" class="muted"> · {{ current.author.college }}</span>
              </el-descriptions-item>
              <el-descriptions-item label="发布时间" :span="2">{{ formatTime(current.createTime) }}</el-descriptions-item>
              <el-descriptions-item label="内容" :span="2">
                <div class="desc-text">{{ current.content }}</div>
              </el-descriptions-item>
            </el-descriptions>

            <el-divider content-position="left">回复（{{ replies.length }}）</el-divider>

            <div v-if="replies.length" class="reply-list">
              <div v-for="r in replies" :key="r.id" class="reply-item">
                <div class="reply-head">
                  <span class="reply-author">
                    {{ r.author?.displayName || ('用户 #' + r.userId) }}
                  </span>
                  <span v-if="r.replyToName" class="reply-to">回复 @{{ r.replyToName }}</span>
                  <span class="reply-time">{{ formatTime(r.createTime) }}</span>
                </div>
                <div class="reply-content">{{ r.content }}</div>
                <div class="reply-actions">
                  <el-button text type="danger" size="small" @click="removeReply(r)">删除该回复</el-button>
                </div>
              </div>
            </div>
            <el-empty v-else description="暂无回复" :image-size="60" />
          </template>
        </div>
      </el-dialog>

      <!-- 分类管理 -->
      <el-dialog v-model="categoryVisible" title="帖子分类管理" width="640px">
        <div class="cat-toolbar">
          <el-button type="primary" size="small" @click="openCatEdit(null)"><el-icon><Plus /></el-icon> 新增分类</el-button>
          <el-button size="small" @click="loadCategories"><el-icon><Refresh /></el-icon> 刷新</el-button>
        </div>
        <el-table :data="categories" stripe style="width: 100%">
          <el-table-column prop="code" label="编码" width="120" />
          <el-table-column prop="name" label="名称" min-width="120" />
          <el-table-column prop="sort" label="排序" width="80" />
          <el-table-column label="状态" width="90">
            <template #default="{ row }">
              <el-tag :type="row.status === 1 ? 'success' : 'info'" size="small">{{ row.status === 1 ? '启用' : '禁用' }}</el-tag>
            </template>
          </el-table-column>
          <el-table-column label="操作" width="180" fixed="right">
            <template #default="{ row }">
              <el-button text type="primary" size="small" @click="openCatEdit(row)">编辑</el-button>
              <el-button text :type="row.status === 1 ? 'warning' : 'success'" size="small" @click="toggleCatStatus(row)">
                {{ row.status === 1 ? '禁用' : '启用' }}
              </el-button>
              <el-button text type="danger" size="small" @click="deleteCat(row)">删除</el-button>
            </template>
          </el-table-column>
        </el-table>
        <el-empty v-if="categories.length === 0" description="暂无分类" :image-size="60" />
      </el-dialog>

      <!-- 分类编辑弹窗 -->
      <el-dialog v-model="catEditVisible" :title="catForm.id ? '编辑分类' : '新增分类'" width="420px">
        <el-form :model="catForm" label-width="70px">
          <el-form-item label="编码">
            <el-input v-model="catForm.code" :disabled="!!catForm.id" placeholder="如：CAMPUS" maxlength="50" />
          </el-form-item>
          <el-form-item label="名称">
            <el-input v-model="catForm.name" placeholder="如：校园动态" maxlength="50" />
          </el-form-item>
          <el-form-item label="排序">
            <el-input-number v-model="catForm.sort" :min="0" :max="999" />
          </el-form-item>
        </el-form>
        <template #footer>
          <el-button @click="catEditVisible = false">取消</el-button>
          <el-button type="primary" :loading="catSaving" @click="saveCat">保存</el-button>
        </template>
      </el-dialog>
    </div>
  </AdminLayout>
</template>

<script setup>
import { onMounted, ref } from 'vue'
import AdminLayout from '@/components/AdminLayout.vue'
import { adminGetPosts, adminDeletePost, adminDeletePostReply, adminGetPostDetail, adminGetPostCategories, adminCreatePostCategory, adminUpdatePostCategory, adminDeletePostCategory } from '@/api'
import { ElMessage, ElMessageBox } from 'element-plus'
import { Plus, Refresh, Menu } from '@element-plus/icons-vue'
import { formatTime, postCategoryMap } from '@/utils/format'

const statusFilter = ref('')
const keyword = ref('')
const posts = ref([])
// 服务端分页：只加载当前页
const page = ref(1)
const size = ref(20)
const total = ref(0)

const detailVisible = ref(false)
const detailLoading = ref(false)
const current = ref(null)
const replies = ref([])
// 分类管理
const categoryVisible = ref(false)
const categories = ref([])
const catEditVisible = ref(false)
const catSaving = ref(false)
const catForm = ref({ id: null, code: '', name: '', sort: 0 })

function categoryText(c) {
  return postCategoryMap[c] || c || '其他'
}

async function loadData() {
  try {
    const res = await adminGetPosts({
      status: statusFilter.value || undefined,
      keyword: keyword.value || undefined,
      page: page.value,
      size: size.value
    })
    posts.value = res.data?.list || []
    total.value = res.data?.total || 0
  } catch {
    /* 拦截器已提示 */
  }
}

function onSizeChange() {
  page.value = 1
  loadData()
}

async function openDetail(row) {
  current.value = row
  replies.value = []
  detailVisible.value = true
  detailLoading.value = true
  try {
    const res = await adminGetPostDetail(row.id)
    if (res?.data) {
      current.value = res.data
      replies.value = res.data.replies || []
    }
  } catch {
    /* 帖子可能已被删除，保留列表中的基本信息 */
  } finally {
    detailLoading.value = false
  }
}

async function removePost(row) {
  try {
    await ElMessageBox.confirm(`确定删除帖子「${row.title}」吗？删除后学生端将不再展示。`, '提示', { type: 'warning' })
  } catch {
    return
  }
  try {
    await adminDeletePost(row.id)
    ElMessage.success('已删除')
    detailVisible.value = false
    loadData()
  } catch {
    /* 拦截器已提示 */
  }
}

async function removeReply(r) {
  try {
    await ElMessageBox.confirm('确定删除该条回复吗？', '提示', { type: 'warning' })
  } catch {
    return
  }
  try {
    await adminDeletePostReply(r.id)
    ElMessage.success('已删除该回复')
    replies.value = replies.value.filter((x) => x.id !== r.id)
    loadData()
  } catch {
    /* 拦截器已提示 */
  }
}

// ==================== 分类管理 ====================
async function openCategoryManage() {
  categoryVisible.value = true
  await loadCategories()
}

async function loadCategories() {
  try {
    const res = await adminGetPostCategories()
    categories.value = res?.data || []
  } catch {
    categories.value = []
  }
}

function openCatEdit(row) {
  if (row) {
    catForm.value = { id: row.id, code: row.code, name: row.name, sort: row.sort || 0 }
  } else {
    catForm.value = { id: null, code: '', name: '', sort: 0 }
  }
  catEditVisible.value = true
}

async function saveCat() {
  if (!catForm.value.code?.trim() && !catForm.value.id) {
    ElMessage.warning('请填写分类编码')
    return
  }
  if (!catForm.value.name?.trim()) {
    ElMessage.warning('请填写分类名称')
    return
  }
  catSaving.value = true
  try {
    if (catForm.value.id) {
      await adminUpdatePostCategory(catForm.value.id, {
        name: catForm.value.name,
        sort: catForm.value.sort
      })
      ElMessage.success('分类已更新')
    } else {
      await adminCreatePostCategory({
        code: catForm.value.code.trim().toUpperCase(),
        name: catForm.value.name.trim(),
        sort: catForm.value.sort
      })
      ElMessage.success('分类已创建')
    }
    catEditVisible.value = false
    await loadCategories()
  } catch {
    /* 拦截器已提示 */
  } finally {
    catSaving.value = false
  }
}

async function toggleCatStatus(row) {
  try {
    await adminUpdatePostCategory(row.id, { status: row.status === 1 ? 0 : 1 })
    ElMessage.success(row.status === 1 ? '已禁用' : '已启用')
    await loadCategories()
  } catch {
    /* 拦截器已提示 */
  }
}

async function deleteCat(row) {
  try {
    await ElMessageBox.confirm(`确定删除分类「${row.name}」吗？已有帖子的分类不受影响。`, '删除确认', { type: 'warning' })
  } catch {
    return
  }
  try {
    await adminDeletePostCategory(row.id)
    ElMessage.success('已删除')
    await loadCategories()
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
.muted {
  color: #909399;
}
.desc-text {
  white-space: pre-wrap;
  line-height: 1.7;
}
.reply-list {
  max-height: 320px;
  overflow-y: auto;
}
.reply-item {
  padding: 10px 12px;
  border-bottom: 1px solid #f2f3f5;
}
.reply-item:last-child {
  border-bottom: none;
}
.reply-head {
  display: flex;
  align-items: center;
  gap: 10px;
  font-size: 13px;
}
.reply-author {
  font-weight: 600;
  color: #303133;
}
.reply-to {
  color: #409eff;
}
.reply-time {
  margin-left: auto;
  color: #c0c4cc;
  font-size: 12px;
}
.reply-content {
  margin-top: 4px;
  font-size: 13px;
  color: #606266;
  line-height: 1.6;
  white-space: pre-wrap;
}
.reply-actions {
  margin-top: 2px;
  text-align: right;
}
</style>
