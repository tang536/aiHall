<template>
  <div class="posts-page">
    <div class="page-header">
      <h2><el-icon><ChatLineSquare /></el-icon> 校园论坛</h2>
      <p>发布校园动态、学习交流与求助，欢迎回复与讨论</p>
    </div>

    <el-tabs v-model="activeTab">
      <el-tab-pane label="全部帖子" name="all">
        <div class="toolbar">
          <el-input
            v-model="query.keyword"
            placeholder="搜索帖子标题或内容"
            clearable
            class="search-input"
            @keyup.enter="loadList"
            @clear="loadList"
          >
            <template #prefix><el-icon><Search /></el-icon></template>
            <template #append><el-button @click="loadList">搜索</el-button></template>
          </el-input>
          <el-button type="primary" @click="openPublish">
            <el-icon><EditPen /></el-icon> 发布帖子
          </el-button>
        </div>

        <div class="category-row">
          <el-button
            v-for="cat in postCategories"
            :key="cat.value"
            size="small"
            :type="query.category === cat.value ? 'primary' : ''"
            @click="switchCategory(cat.value)"
          >
            {{ cat.label }}
          </el-button>
        </div>

        <div v-loading="loading" class="post-list">
          <div v-for="post in list" :key="post.id" class="post-card card-hover" @click="goDetail(post.id)">
            <div class="post-head">
              <el-avatar :size="38" class="avatar clickable" :src="post.author?.avatar ? resolveAvatarUrl(post.author.avatar) : ''" @click.stop="goUserProfile(post.author)">{{ (post.author?.displayName || '?').charAt(0) }}</el-avatar>
              <div class="head-info">
                <div class="author-line">
                  <span class="author clickable" @click.stop="goUserProfile(post.author)">{{ post.author?.displayName }}</span>
                  <el-tag size="small" effect="plain">{{ categoryLabelMap[post.category] || '其他' }}</el-tag>
                </div>
                <div class="sub-line">
                  <span v-if="post.author?.college">{{ post.author.college }}</span>
                  <span>{{ relativeTime(post.createTime) }}</span>
                </div>
              </div>
            </div>
            <h4 class="post-title">{{ post.title || '（无标题）' }}</h4>
            <p class="post-excerpt">{{ excerpt(post.content) }}</p>
            <div v-if="splitImages(post.images).length" class="post-images">
              <el-image
                v-for="(url, idx) in splitImages(post.images).slice(0, 3)"
                :key="idx"
                :src="url"
                fit="cover"
                class="thumb"
              />
            </div>
            <div class="post-foot">
              <span><el-icon><ChatDotRound /></el-icon> {{ post.replyCount || 0 }} 条回复</span>
              <span><el-icon><Pointer /></el-icon> {{ post.likeCount || 0 }} 赞</span>
            </div>
          </div>
        </div>
        <el-empty v-if="!loading && list.length === 0" description="还没有帖子，来发布第一条吧" />

        <div v-if="total > query.size" class="pager">
          <el-pagination
            layout="prev, pager, next"
            :total="total"
            :page-size="query.size"
            :current-page="query.page"
            @current-change="onPageChange"
          />
        </div>
      </el-tab-pane>

      <el-tab-pane label="我的帖子" name="mine">
        <el-table :data="mineList" v-loading="mineLoading" empty-text="还没有发布过帖子">
          <el-table-column prop="title" label="标题" min-width="180" show-overflow-tooltip>
            <template #default="{ row }">{{ row.title || '（无标题）' }}</template>
          </el-table-column>
          <el-table-column label="分类" width="110">
            <template #default="{ row }">{{ categoryLabelMap[row.category] || '其他' }}</template>
          </el-table-column>
          <el-table-column label="回复 / 赞" width="110">
            <template #default="{ row }">{{ row.replyCount || 0 }} / {{ row.likeCount || 0 }}</template>
          </el-table-column>
          <el-table-column label="发布时间" width="170">
            <template #default="{ row }">{{ (row.createTime || '').replace('T', ' ').slice(0, 16) }}</template>
          </el-table-column>
          <el-table-column label="操作" width="150" fixed="right">
            <template #default="{ row }">
              <el-button size="small" text type="primary" @click="goDetail(row.id)">查看</el-button>
              <el-button size="small" text type="danger" @click="doDelete(row)">删除</el-button>
            </template>
          </el-table-column>
        </el-table>
      </el-tab-pane>

      <!-- 我的收藏 -->
      <el-tab-pane label="我的收藏" name="favorites">
        <div v-loading="favoriteLoading" class="post-list">
          <div v-for="post in favoriteList" :key="post.id" class="post-card card-hover" @click="goDetail(post.id)">
            <div class="post-head">
              <el-avatar :size="38" class="avatar clickable" :src="post.author?.avatar ? resolveAvatarUrl(post.author.avatar) : ''" @click.stop="goUserProfile(post.author)">{{ (post.author?.displayName || '?').charAt(0) }}</el-avatar>
              <div class="head-info">
                <div class="author-line">
                  <span class="author clickable" @click.stop="goUserProfile(post.author)">{{ post.author?.displayName }}</span>
                  <el-tag size="small" effect="plain">{{ categoryLabelMap[post.category] || '其他' }}</el-tag>
                </div>
                <div class="sub-line">
                  <span v-if="post.author?.college">{{ post.author.college }}</span>
                  <span>{{ relativeTime(post.createTime) }}</span>
                </div>
              </div>
            </div>
            <h4 class="post-title">{{ post.title || '（无标题）' }}</h4>
            <p class="post-excerpt">{{ excerpt(post.content) }}</p>
            <div v-if="splitImages(post.images).length" class="post-images">
              <el-image
                v-for="(url, idx) in splitImages(post.images).slice(0, 3)"
                :key="idx"
                :src="url"
                fit="cover"
                class="thumb"
              />
            </div>
            <div class="post-foot">
              <span><el-icon><ChatDotRound /></el-icon> {{ post.replyCount || 0 }} 条回复</span>
              <span><el-icon><Pointer /></el-icon> {{ post.likeCount || 0 }} 赞</span>
            </div>
          </div>
          <el-empty v-if="!favoriteLoading && favoriteList.length === 0" description="还没有收藏帖子，去逛逛吧" :image-size="80" />
        </div>
      </el-tab-pane>
    </el-tabs>

    <!-- 发布帖子 -->
    <el-dialog v-model="publishVisible" title="发布帖子" width="620px" top="6vh">
      <el-form :model="form" label-width="70px" :rules="rules" ref="formRef">
        <el-form-item label="标题" prop="title">
          <el-input v-model="form.title" maxlength="120" show-word-limit placeholder="一句话说明你想聊什么" />
        </el-form-item>
        <el-form-item label="分类">
          <el-select v-model="form.category" style="width: 100%">
            <el-option v-for="c in postCategories.slice(1)" :key="c.value" :label="c.label" :value="c.value" />
          </el-select>
        </el-form-item>
        <el-form-item label="内容" prop="content">
          <el-input
            v-model="form.content"
            type="textarea"
            :rows="6"
            maxlength="4000"
            show-word-limit
            placeholder="详细说说你的想法、问题或分享内容"
          />
        </el-form-item>
        <el-form-item label="配图">
          <ImageUpload v-model="form.images" :max="4" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="publishVisible = false">取消</el-button>
        <el-button type="primary" :loading="submitting" @click="submitPost">发布</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup>
import { onMounted, reactive, ref, watch } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage, ElMessageBox } from 'element-plus'
import ImageUpload from '@/components/ImageUpload.vue'
import { listPosts, createPost, deletePost, myPosts, myPostFavorites, getPostCategories } from '@/api'
import { POST_CATEGORIES, POST_CATEGORY_LABEL, splitImages, relativeTime, resolveAvatarUrl } from '@/utils/community'

const router = useRouter()

const activeTab = ref('all')
const loading = ref(false)
const list = ref([])
const total = ref(0)
const query = reactive({ keyword: '', category: 'ALL', page: 1, size: 10 })

const mineList = ref([])
const mineLoading = ref(false)
const favoriteList = ref([])
const favoriteLoading = ref(false)
// 动态分类（从后端加载，兜底用硬编码）
const postCategories = ref(POST_CATEGORIES)
const categoryLabelMap = ref({ ...POST_CATEGORY_LABEL })

async function loadCategories() {
  try {
    const res = await getPostCategories()
    const list = res?.data || []
    if (list.length) {
      postCategories.value = [{ value: 'ALL', label: '全部' }, ...list.map(c => ({ value: c.code, label: c.name }))]
      const map = {}
      list.forEach(c => { map[c.code] = c.name })
      categoryLabelMap.value = map
    }
  } catch {
    /* 兜底用硬编码 */
  }
}

const publishVisible = ref(false)
const submitting = ref(false)
const formRef = ref(null)
const form = reactive({ title: '', content: '', category: 'CAMPUS', images: '' })
const rules = {
  title: [{ required: true, message: '请填写帖子标题', trigger: 'blur' }],
  content: [{ required: true, message: '请填写帖子内容', trigger: 'blur' }]
}

function excerpt(text) {
  const s = (text || '').replace(/\s+/g, ' ').trim()
  return s.length > 110 ? s.slice(0, 110) + '…' : s
}

async function loadList() {
  loading.value = true
  try {
    const res = await listPosts({ ...query })
    list.value = res?.data?.list || []
    total.value = res?.data?.total || 0
  } finally {
    loading.value = false
  }
}

function switchCategory(value) {
  query.category = value
  query.page = 1
  loadList()
}

function onPageChange(page) {
  query.page = page
  loadList()
}

async function loadMine() {
  mineLoading.value = true
  try {
    const res = await myPosts()
    mineList.value = res?.data || []
  } finally {
    mineLoading.value = false
  }
}

async function loadFavorites() {
  favoriteLoading.value = true
  try {
    const res = await myPostFavorites()
    favoriteList.value = res?.data || []
  } catch {
    favoriteList.value = []
  } finally {
    favoriteLoading.value = false
  }
}

function goDetail(id) {
  router.push(`/posts/${id}`)
}

function goUserProfile(user) {
  if (!user?.userId) return
  router.push(`/user/${user.userId}`)
}

function openPublish() {
  Object.assign(form, { title: '', content: '', category: 'CAMPUS', images: '' })
  publishVisible.value = true
}

async function submitPost() {
  try {
    await formRef.value.validate()
  } catch {
    return
  }
  submitting.value = true
  try {
    const res = await createPost({ ...form })
    ElMessage.success('发布成功')
    publishVisible.value = false
    const id = res?.data?.id
    if (id) {
      router.push(`/posts/${id}`)
    } else {
      loadList()
    }
  } finally {
    submitting.value = false
  }
}

async function doDelete(row) {
  try {
    await ElMessageBox.confirm(`确定删除帖子「${row.title || '（无标题）'}」吗？删除后无法恢复。`, '删除确认', {
      confirmButtonText: '确定删除', cancelButtonText: '取消', type: 'warning'
    })
  } catch {
    return
  }
  await deletePost(row.id)
  ElMessage.success('已删除')
  loadMine()
  loadList()
}

watch(activeTab, (tab) => {
  if (tab === 'mine') loadMine()
  if (tab === 'favorites') loadFavorites()
})

onMounted(() => {
  loadList()
  loadCategories()
})
</script>


      param($m) $m.Groups[1].Value -replace 'border-radius:\s*\d+px;', 'border-radius: 0;'
    
