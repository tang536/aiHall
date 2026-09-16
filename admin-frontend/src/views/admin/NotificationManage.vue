<template>
  <AdminLayout>
    <div class="manage-page">
      <div class="page-header">
        <h2>通知管理</h2>
        <p>发布和管理校园通知公告，支持 PDF 批量导入，通知可加入知识库供 AI 检索</p>
      </div>

      <div class="toolbar">
        <el-input v-model="searchKeyword" placeholder="搜索通知标题..." clearable style="width: 240px" @keyup.enter="page = 1">
          <template #prefix><el-icon><Search /></el-icon></template>
        </el-input>
        <el-select v-model="filterCategory" placeholder="全部分类" clearable style="width: 140px">
          <el-option label="校园公告" value="校园公告" />
          <el-option label="教务" value="教务" />
          <el-option label="学工" value="学工" />
          <el-option label="后勤" value="后勤" />
          <el-option label="社团" value="社团" />
          <el-option label="紧急通知" value="紧急通知" />
        </el-select>
        <div class="toolbar-spacer"></div>
        <el-button type="primary" @click="showCreate"><el-icon><Plus /></el-icon> 发布通知</el-button>
        <el-button type="success" @click="showPdfImport"><el-icon><UploadFilled /></el-icon> 导入PDF</el-button>
      </div>

      <el-table :data="pagedNotifications" stripe style="width: 100%" v-loading="loading">
        <el-table-column type="index" label="#" width="60" :index="indexMethod" />
        <el-table-column prop="title" label="标题" min-width="220" show-overflow-tooltip />
        <el-table-column label="类别" width="90">
          <template #default="{ row }"><el-tag size="small">{{ row.category }}</el-tag></template>
        </el-table-column>
        <el-table-column prop="department" label="发布部门" width="120" show-overflow-tooltip />
        <el-table-column label="置顶" width="60" align="center">
          <template #default="{ row }"><el-tag v-if="row.isTop" type="danger" size="small">置顶</el-tag></template>
        </el-table-column>
        <el-table-column label="紧急" width="60" align="center">
          <template #default="{ row }"><el-tag v-if="row.isEmergency" type="danger" effect="dark" size="small">紧急</el-tag></template>
        </el-table-column>
        <el-table-column prop="viewCount" label="阅读量" width="80" align="center" />
        <el-table-column label="发布时间" width="160">
          <template #default="{ row }">{{ formatTime(row.publishTime) }}</template>
        </el-table-column>
        <el-table-column label="操作" width="240" fixed="right">
          <template #default="{ row }">
            <el-button text type="primary" size="small" @click="showEdit(row)">编辑</el-button>
            <el-button text type="success" size="small" @click="addToKnowledge(row)">加入知识库</el-button>
            <el-button text type="danger" size="small" @click="deleteItem(row)">删除</el-button>
          </template>
        </el-table-column>
      </el-table>

      <div class="pagination">
        <el-pagination
          v-model:current-page="page"
          v-model:page-size="size"
          :total="filteredNotifications.length"
          :page-sizes="[10, 20, 50]"
          layout="total, sizes, prev, pager, next"
          background
        />
      </div>

      <!-- 导入PDF弹窗 -->
      <el-dialog v-model="pdfDialogVisible" title="导入 PDF 通知" width="600px" top="8vh">
        <el-form :model="pdfForm" label-width="90px">
          <el-form-item label="PDF文件" required>
            <el-upload
              ref="pdfUploadRef"
              :auto-upload="false"
              :limit="1"
              accept=".pdf"
              :on-change="onPdfChange"
              :on-remove="onPdfRemove"
              drag
            >
              <el-icon class="el-icon--upload"><UploadFilled /></el-icon>
              <div class="el-upload__text">将 PDF 文件拖到此处，或<em>点击选择</em></div>
              <template #tip>
                <div class="el-upload__tip">仅支持 PDF 格式，最大 10MB；扫描件/图片型 PDF 无法提取文字</div>
              </template>
            </el-upload>
          </el-form-item>
          <el-form-item label="标题">
            <el-input v-model="pdfForm.title" placeholder="留空则使用 PDF 文件名作为标题" maxlength="200" />
          </el-form-item>
          <el-form-item label="类别">
            <el-select v-model="pdfForm.category" style="width: 100%">
              <el-option label="校园公告" value="校园公告" />
              <el-option label="教务" value="教务" />
              <el-option label="学工" value="学工" />
              <el-option label="后勤" value="后勤" />
              <el-option label="社团" value="社团" />
              <el-option label="紧急通知" value="紧急通知" />
            </el-select>
          </el-form-item>
          <el-form-item label="发布部门">
            <el-input v-model="pdfForm.department" placeholder="留空则默认「PDF导入」" />
          </el-form-item>
        </el-form>
        <template #footer>
          <el-button @click="pdfDialogVisible = false">取消</el-button>
          <el-button type="primary" :loading="pdfImporting" @click="submitPdfImport">确认导入</el-button>
        </template>
      </el-dialog>

      <!-- 编辑弹窗 -->
      <el-dialog v-model="dialogVisible" :title="form.id ? '编辑通知' : '发布通知'" width="640px" top="5vh">
        <el-form :model="form" label-width="90px">
          <el-form-item label="标题" required>
            <el-input v-model="form.title" placeholder="请输入通知标题" />
          </el-form-item>
          <el-form-item label="类别">
            <el-select v-model="form.category" style="width: 100%">
              <el-option label="校园公告" value="校园公告" />
              <el-option label="教务" value="教务" />
              <el-option label="学工" value="学工" />
              <el-option label="后勤" value="后勤" />
              <el-option label="社团" value="社团" />
              <el-option label="紧急通知" value="紧急通知" />
            </el-select>
          </el-form-item>
          <el-form-item label="发布部门">
            <el-input v-model="form.department" placeholder="请输入发布部门" />
          </el-form-item>
          <el-form-item label="摘要">
            <el-input v-model="form.summary" type="textarea" :rows="2" placeholder="通知摘要（选填）" />
          </el-form-item>
          <el-form-item label="正文" required>
            <el-input v-model="form.content" type="textarea" :rows="8" placeholder="请输入通知正文内容" />
          </el-form-item>
          <el-form-item label="选项">
            <el-switch v-model="form.isTop" active-text="置顶" style="margin-right: 20px" />
            <el-switch v-model="form.isEmergency" active-text="紧急通知" />
          </el-form-item>
        </el-form>
        <template #footer>
          <el-button @click="dialogVisible = false">取消</el-button>
          <el-button type="primary" @click="submit">保存</el-button>
        </template>
      </el-dialog>

      <!-- 加入知识库弹窗 -->
      <el-dialog v-model="knowledgeVisible" title="加入知识库" width="640px" top="5vh">
        <el-form :model="knowledgeForm" label-width="90px">
          <el-form-item label="标题" required>
            <el-input v-model="knowledgeForm.title" placeholder="文档标题" />
          </el-form-item>
          <el-form-item label="分类">
            <el-select v-model="knowledgeForm.category" style="width: 100%">
              <el-option label="规章制度" value="规章制度" />
              <el-option label="办事流程" value="办事流程" />
              <el-option label="常见问题" value="常见问题" />
              <el-option label="部门联系方式" value="部门联系方式" />
              <el-option label="奖助政策" value="奖助政策" />
              <el-option label="通知公告" value="通知公告" />
            </el-select>
          </el-form-item>
          <el-form-item label="来源">
            <el-input v-model="knowledgeForm.source" placeholder="文档来源" />
          </el-form-item>
          <el-form-item label="关键词">
            <el-input v-model="knowledgeForm.keywords" placeholder="关键词，用逗号分隔（用于AI检索匹配）" />
          </el-form-item>
          <el-form-item label="优先级">
            <el-input-number v-model="knowledgeForm.priority" :min="0" :max="100" />
            <span style="margin-left: 8px; color: #909399; font-size: 12px">数值越高，AI检索时越优先匹配</span>
          </el-form-item>
          <el-form-item label="文档内容" required>
            <el-input v-model="knowledgeForm.content" type="textarea" :rows="10" placeholder="请输入文档正文内容" />
          </el-form-item>
        </el-form>
        <template #footer>
          <el-button @click="knowledgeVisible = false">取消</el-button>
          <el-button type="primary" @click="submitKnowledge">确认加入</el-button>
        </template>
      </el-dialog>
    </div>
  </AdminLayout>
</template>

<script setup>
import { ref, reactive, computed, onMounted } from 'vue'
import AdminLayout from '@/components/AdminLayout.vue'
import { getNotifications, adminCreateNotification, adminUpdateNotification, adminDeleteNotification, adminCreateKnowledge, importPdfNotification } from '@/api'
import { ElMessage, ElMessageBox } from 'element-plus'
import { UploadFilled, Search, Plus } from '@element-plus/icons-vue'
import { formatTime } from '@/utils/format'

const notifications = ref([])
const loading = ref(false)
const dialogVisible = ref(false)
const knowledgeVisible = ref(false)
const pdfDialogVisible = ref(false)
const pdfImporting = ref(false)
const pdfFile = ref(null)
const pdfUploadRef = ref()

// 搜索与分页
const searchKeyword = ref('')
const filterCategory = ref('')
const page = ref(1)
const size = ref(10)

const form = reactive({ id: null, title: '', category: '校园公告', department: '', summary: '', content: '', isTop: false, isEmergency: false })
const knowledgeForm = reactive({ title: '', category: '通知公告', source: '校园公告', keywords: '', content: '', priority: 50, status: 1 })
const pdfForm = reactive({ title: '', category: '校园公告', department: '' })

const filteredNotifications = computed(() => {
  return notifications.value.filter(n => {
    const matchKeyword = !searchKeyword.value || n.title?.toLowerCase().includes(searchKeyword.value.toLowerCase())
    const matchCategory = !filterCategory.value || n.category === filterCategory.value
    return matchKeyword && matchCategory
  })
})

const pagedNotifications = computed(() => {
  const start = (page.value - 1) * size.value
  return filteredNotifications.value.slice(start, start + size.value)
})

function indexMethod(index) {
  return (page.value - 1) * size.value + index + 1
}

function showPdfImport() {
  Object.assign(pdfForm, { title: '', category: '校园公告', department: '' })
  pdfFile.value = null
  pdfUploadRef.value?.clearFiles()
  pdfDialogVisible.value = true
}

function onPdfChange(file) {
  pdfFile.value = file.raw
}

function onPdfRemove() {
  pdfFile.value = null
}

async function submitPdfImport() {
  if (!pdfFile.value) {
    ElMessage.warning('请先选择 PDF 文件')
    return
  }
  pdfImporting.value = true
  try {
    const formData = new FormData()
    formData.append('file', pdfFile.value)
    formData.append('title', pdfForm.title)
    formData.append('category', pdfForm.category)
    formData.append('department', pdfForm.department)
    const res = await importPdfNotification(formData)
    ElMessage.success(`导入成功：${res.data.title}`)
    pdfDialogVisible.value = false
    await loadData()
  } catch (e) {
    const msg = e.response?.data?.message || e.message
    ElMessage.error(msg && msg !== 'Failed to fetch' ? msg : '导入失败，请稍后重试')
  } finally {
    pdfImporting.value = false
  }
}

function showCreate() {
  Object.assign(form, { id: null, title: '', category: '校园公告', department: '', summary: '', content: '', isTop: false, isEmergency: false })
  dialogVisible.value = true
}

function showEdit(row) {
  Object.assign(form, row)
  dialogVisible.value = true
}

async function submit() {
  if (!form.title || !form.content) { ElMessage.warning('请填写标题和正文'); return }
  try {
    if (form.id) {
      await adminUpdateNotification(form.id, form)
      ElMessage.success('更新成功')
    } else {
      await adminCreateNotification(form)
      ElMessage.success('发布成功')
    }
    dialogVisible.value = false
    loadData()
  } catch {}
}

async function deleteItem(row) {
  try {
    await ElMessageBox.confirm(`确定删除通知「${row.title}」吗？`, '确认删除', { type: 'warning' })
    await adminDeleteNotification(row.id)
    ElMessage.success('删除成功')
    loadData()
  } catch {}
}

function addToKnowledge(row) {
  Object.assign(knowledgeForm, {
    title: row.title,
    category: '通知公告',
    source: row.department || '校园公告',
    keywords: row.title,
    content: row.content || row.summary || '',
    priority: 50,
    status: 1
  })
  knowledgeVisible.value = true
}

async function submitKnowledge() {
  if (!knowledgeForm.title || !knowledgeForm.content) {
    ElMessage.warning('请填写标题和内容')
    return
  }
  try {
    await adminCreateKnowledge(knowledgeForm)
    ElMessage.success('已加入知识库')
    knowledgeVisible.value = false
  } catch (e) {
    ElMessage.warning(e?.response?.data?.message || '您已加入知识库')
  }
}

async function loadData() {
  loading.value = true
  try {
    const res = await getNotifications({})
    notifications.value = res.data
  } catch {
  } finally {
    loading.value = false
  }
}

onMounted(() => loadData())
</script>

<style scoped>
.manage-page { display: flex; flex-direction: column; gap: 16px; }

.page-header h2 {
  margin: 0 0 4px 0;
  font-size: 20px;
  font-weight: 600;
  color: #303133;
}

.page-header p {
  margin: 0;
  font-size: 13px;
  color: #909399;
}

.toolbar {
  display: flex;
  align-items: center;
  gap: 10px;
}

.toolbar-spacer {
  flex: 1;
}

.pagination {
  display: flex;
  justify-content: flex-end;
}
</style>
