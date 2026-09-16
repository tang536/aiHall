<template>
  <AdminLayout>
    <div class="manage-page">
      <div class="toolbar">
        <el-select v-model="categoryFilter" placeholder="按分类筛选" clearable style="width: 180px" @change="loadData">
          <el-option label="规章制度" value="规章制度" />
          <el-option label="办事流程" value="办事流程" />
          <el-option label="常见问题" value="常见问题" />
          <el-option label="部门联系方式" value="部门联系方式" />
          <el-option label="奖助政策" value="奖助政策" />
        </el-select>
        <el-button type="primary" @click="showCreate"><el-icon><Plus /></el-icon> 添加文档</el-button>
      </div>

      <el-table :data="docs" stripe style="width: 100%">
        <el-table-column prop="title" label="文档标题" min-width="200" show-overflow-tooltip />
        <el-table-column label="分类" width="120">
          <template #default="{ row }"><el-tag size="small">{{ row.category }}</el-tag></template>
        </el-table-column>
        <el-table-column prop="source" label="来源" width="120" show-overflow-tooltip />
        <el-table-column prop="keywords" label="关键词" min-width="160" show-overflow-tooltip />
        <el-table-column prop="priority" label="优先级" width="80" sortable />
        <el-table-column label="状态" width="80">
          <template #default="{ row }"><el-tag :type="row.status === 1 ? 'success' : 'info'" size="small">{{ row.status === 1 ? '生效' : '禁用' }}</el-tag></template>
        </el-table-column>
        <el-table-column label="更新时间" width="160">
          <template #default="{ row }">{{ formatTime(row.updateTime) }}</template>
        </el-table-column>
        <el-table-column label="操作" width="160" fixed="right">
          <template #default="{ row }">
            <el-button text type="primary" size="small" @click="showEdit(row)">编辑</el-button>
            <el-button text type="danger" size="small" @click="deleteItem(row)">删除</el-button>
          </template>
        </el-table-column>
      </el-table>

      <!-- 编辑弹窗 -->
      <el-dialog v-model="dialogVisible" :title="form.id ? '编辑文档' : '添加文档'" width="640px" top="5vh">
        <el-form :model="form" label-width="90px">
          <el-form-item label="标题" required>
            <el-input v-model="form.title" placeholder="文档标题" />
          </el-form-item>
          <el-form-item label="分类">
            <el-select v-model="form.category" style="width: 100%">
              <el-option label="规章制度" value="规章制度" />
              <el-option label="办事流程" value="办事流程" />
              <el-option label="常见问题" value="常见问题" />
              <el-option label="部门联系方式" value="部门联系方式" />
              <el-option label="奖助政策" value="奖助政策" />
            </el-select>
          </el-form-item>
          <el-form-item label="来源">
            <el-input v-model="form.source" placeholder="文档来源（如：学校官网、部门文件）" />
          </el-form-item>
          <el-form-item label="关键词">
            <el-input v-model="form.keywords" placeholder="关键词，用逗号分隔（用于AI检索匹配）" />
          </el-form-item>
          <el-form-item label="优先级">
            <el-input-number v-model="form.priority" :min="0" :max="100" />
            <span style="margin-left: 8px; color: #909399; font-size: 12px">数值越高，AI检索时越优先匹配</span>
          </el-form-item>
          <el-form-item label="文档内容" required>
            <el-input v-model="form.content" type="textarea" :rows="10" placeholder="请输入文档正文内容，AI将基于此内容回答学生问题" />
          </el-form-item>
          <el-form-item label="状态">
            <el-switch v-model="form.status" :active-value="1" :inactive-value="0" active-text="生效" inactive-text="禁用" />
          </el-form-item>
        </el-form>
        <template #footer>
          <el-button @click="dialogVisible = false">取消</el-button>
          <el-button type="primary" @click="submit">保存</el-button>
        </template>
      </el-dialog>
    </div>
  </AdminLayout>
</template>

<script setup>
import { ref, reactive, onMounted } from 'vue'
import AdminLayout from '@/components/AdminLayout.vue'
import { adminGetKnowledge, adminCreateKnowledge, adminUpdateKnowledge, adminDeleteKnowledge } from '@/api'
import { ElMessage, ElMessageBox } from 'element-plus'
import { formatTime } from '@/utils/format'

const docs = ref([])
const categoryFilter = ref('')
const dialogVisible = ref(false)
const form = reactive({ id: null, title: '', category: '规章制度', source: '', keywords: '', priority: 0, content: '', status: 1 })

function showCreate() {
  Object.assign(form, { id: null, title: '', category: '规章制度', source: '', keywords: '', priority: 0, content: '', status: 1 })
  dialogVisible.value = true
}

function showEdit(row) {
  Object.assign(form, row)
  dialogVisible.value = true
}

async function submit() {
  if (!form.title || !form.content) { ElMessage.warning('请填写标题和内容'); return }
  try {
    if (form.id) {
      await adminUpdateKnowledge(form.id, form)
      ElMessage.success('更新成功')
    } else {
      await adminCreateKnowledge(form)
      ElMessage.success('添加成功')
    }
    dialogVisible.value = false
    loadData()
  } catch {}
}

async function deleteItem(row) {
  try {
    await ElMessageBox.confirm(`确定删除文档「${row.title}」吗？`, '确认删除', { type: 'warning' })
    await adminDeleteKnowledge(row.id)
    ElMessage.success('删除成功')
    loadData()
  } catch {}
}

async function loadData() {
  try {
    const res = await adminGetKnowledge(categoryFilter.value || undefined)
    docs.value = res.data
  } catch {}
}

onMounted(() => loadData())
</script>

<style scoped>
.manage-page { display: flex; flex-direction: column; gap: 16px; }
.toolbar { display: flex; gap: 12px; }
</style>
