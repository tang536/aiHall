<template>
  <div class="schedule-page">
    <div class="page-header">
      <h2><el-icon><Calendar /></el-icon> 课表与考试查询</h2>
      <p>查看个人课表、考试安排，支持日/周视图切换</p>
    </div>

    <el-tabs v-model="activeTab" class="main-tabs">
      <!-- 课表 -->
      <el-tab-pane label="我的课表" name="courses">
        <div class="toolbar">
          <el-radio-group v-model="viewMode" size="default" :disabled="editMode">
            <el-radio-button value="week">周视图</el-radio-button>
            <el-radio-button value="list">列表视图</el-radio-button>
          </el-radio-group>
          <!-- 周次选择器 -->
          <div class="week-selector" v-if="viewMode === 'week'">
            <el-button circle size="small" @click="prevWeek" :disabled="currentWeek <= 1">
              <el-icon><ArrowLeft /></el-icon>
            </el-button>
            <span class="week-label" @click="goToCurrentWeek" title="点击回到本周">
              第 {{ currentWeek }} 周
              <el-tag v-if="currentWeek === calculateCurrentWeek()" size="small" type="success" style="margin-left:6px">本周</el-tag>
            </span>
            <el-button circle size="small" @click="nextWeek" :disabled="currentWeek >= TOTAL_WEEKS">
              <el-icon><ArrowRight /></el-icon>
            </el-button>
          </div>
          <div class="toolbar-right">
            <el-button @click="openSettings" style="margin-right:8px">
              <el-icon><Setting /></el-icon> 课表设置
            </el-button>
            <el-tag v-if="!userStore.isLoggedIn" type="warning" style="margin-right:12px">
              未登录，仅显示公共课程列表
            </el-tag>
            <template v-if="!editMode">
              <el-button v-if="userStore.isLoggedIn && courses.length > 0" @click="enterEditMode" style="margin-right:8px">
                <el-icon><Edit /></el-icon> 编辑课表
              </el-button>
              <el-button @click="handleSyncJwxt" :disabled="!userStore.isLoggedIn || syncing" :loading="syncing" style="margin-right:8px">
                <el-icon><Refresh /></el-icon> {{ syncing ? '同步中...' : '从教务系统同步' }}
              </el-button>
              <el-button type="primary" @click="openImportDialog" :disabled="!userStore.isLoggedIn">
                <el-icon><Upload /></el-icon> 导入课表
              </el-button>
            </template>
            <template v-else>
              <el-button @click="addCourse" style="margin-right:8px">
                <el-icon><Plus /></el-icon> 添加课程
              </el-button>
              <el-button @click="cancelEdit" style="margin-right:8px">取消</el-button>
              <el-button type="primary" :loading="savingEdit" @click="saveEdit">
                <el-icon><Check /></el-icon> 保存
              </el-button>
            </template>
          </div>
        </div>

        <!-- 空课表状态 -->
        <div v-if="courses.length === 0" class="empty-schedule">
          <el-empty description="暂无课表，点击下方按钮导入">
            <el-button type="primary" @click="openImportDialog" :disabled="!userStore.isLoggedIn">
              <el-icon><Upload /></el-icon> 导入课表
            </el-button>
            <p v-if="!userStore.isLoggedIn" style="color:#909399;margin-top:8px;font-size:13px">请先登录后导入个人课程</p>
          </el-empty>
        </div>

        <!-- 周视图 -->
        <div v-else-if="viewMode === 'week'" class="timetable">
          <div class="timetable-header">
            <div class="time-col">节次</div>
            <div v-for="d in weekDays" :key="d.day" class="day-col"
                 :class="{ today: d.day === today && currentWeek === calculateCurrentWeek() }">
              {{ d.name }}
              <span class="date">{{ d.date }}</span>
            </div>
          </div>
          <div class="timetable-body">
            <div v-for="sec in sections" :key="sec" class="time-row">
              <div class="time-col">
                <span class="section-num">第{{ sec }}节</span>
                <span class="section-time">{{ getSectionTime(sec) }}</span>
              </div>
              <div v-for="d in weekDays" :key="d.day" class="day-col">
                <div v-for="course in getActiveCoursesAt(d.day, sec)" :key="course.id"
                     class="course-block"
                     :style="{ background: getCourseColor(course.courseName) }"
                     @click="showCourseDetail(course)">
                  <div class="course-name">{{ course.courseName }}</div>
                  <div class="course-room">{{ course.classroom }}</div>
                  <div class="course-teacher">{{ course.teacher }}</div>
                </div>
              </div>
            </div>
          </div>
        </div>

        <!-- 列表视图 -->
        <div v-else class="course-list">
          <el-table :data="editMode ? editingCourses : courses" stripe style="width: 100%">
            <el-table-column label="课程名称" min-width="160" show-overflow-tooltip>
              <template #default="{ row }">
                <el-input v-if="editMode" v-model="row.courseName" size="small" placeholder="课程名" />
                <span v-else>{{ row.courseName }}</span>
              </template>
            </el-table-column>
            <el-table-column label="教师" width="90">
              <template #default="{ row }">
                <el-input v-if="editMode" v-model="row.teacher" size="small" placeholder="教师" />
                <span v-else>{{ row.teacher }}</span>
              </template>
            </el-table-column>
            <el-table-column label="教室" width="120">
              <template #default="{ row }">
                <el-input v-if="editMode" v-model="row.classroom" size="small" placeholder="教室" />
                <span v-else>{{ row.classroom }}</span>
              </template>
            </el-table-column>
            <el-table-column label="星期" width="80">
              <template #default="{ row }">
                <el-select v-if="editMode" v-model="row.dayOfWeek" size="small" style="width:100%">
                  <el-option v-for="d in 7" :key="d" :label="getDayName(d)" :value="d" />
                </el-select>
                <span v-else>{{ getDayName(row.dayOfWeek) }}</span>
              </template>
            </el-table-column>
            <el-table-column label="节次" width="120">
              <template #default="{ row }">
                <template v-if="editMode">
                  <el-select v-model="row.startSection" size="small" style="width:52px">
                    <el-option v-for="s in 12" :key="s" :label="s" :value="s" />
                  </el-select>
                  <span style="margin:0 2px">-</span>
                  <el-select v-model="row.endSection" size="small" style="width:52px">
                    <el-option v-for="s in 12" :key="s" :label="s" :value="s" />
                  </el-select>
                </template>
                <span v-else>第{{ row.startSection }}-{{ row.endSection }}节</span>
              </template>
            </el-table-column>
            <el-table-column label="周次" width="90">
              <template #default="{ row }">
                <el-input v-if="editMode" v-model="row.weekRange" size="small" placeholder="如1-16周" />
                <span v-else>{{ row.weekRange }}</span>
              </template>
            </el-table-column>
            <el-table-column label="学分" width="70">
              <template #default="{ row }">
                <el-input v-if="editMode" v-model="row.credits" size="small" placeholder="学分" />
                <span v-else>{{ row.credits }}</span>
              </template>
            </el-table-column>
            <el-table-column label="操作" width="100" fixed="right">
              <template #default="{ row, $index }">
                <el-button v-if="editMode" text type="danger" size="small" @click="removeCourse($index)">删除</el-button>
                <el-button v-else text type="primary" @click="showCourseDetail(row)">详情</el-button>
              </template>
            </el-table-column>
          </el-table>
          <el-empty v-if="editMode && editingCourses.length === 0" description="暂无课程，点击上方「添加课程」按钮添加" :image-size="80" />
        </div>
      </el-tab-pane>

      <!-- 考试 -->
      <el-tab-pane label="考试安排" name="exams">
        <div class="toolbar" style="margin-bottom:16px">
          <div class="toolbar-right">
            <el-button @click="handleSyncJwxt" :disabled="!userStore.isLoggedIn || syncing" :loading="syncing">
              <el-icon><Refresh /></el-icon> {{ syncing ? '同步中...' : '从教务系统同步' }}
            </el-button>
          </div>
        </div>
        <div class="exam-list">
          <div v-for="exam in exams" :key="exam.id"
               class="exam-card card-hover"
               :class="{ ended: exam.status === 2 }">
            <div class="exam-date">
              <div class="exam-month">{{ getMonth(exam.examTime) }}月</div>
              <div class="exam-day">{{ getDay(exam.examTime) }}</div>
            </div>
            <div class="exam-info">
              <h4>{{ exam.examName }}</h4>
              <div class="exam-meta">
                <span><el-icon><Clock /></el-icon> {{ formatDateTime(exam.examTime) }}</span>
                <span><el-icon><Location /></el-icon> {{ exam.location }}</span>
                <span v-if="exam.seatNumber"><el-icon><Postcard /></el-icon> 座位：{{ exam.seatNumber }}</span>
              </div>
              <div class="exam-tags">
                <el-tag size="small">{{ exam.examType }}</el-tag>
                <el-tag v-if="isComing(exam.examTime)" type="danger" size="small" effect="dark">
                  {{ getCountdown(exam.examTime) }}
                </el-tag>
                <el-tag v-if="exam.status === 2" type="info" size="small">已结束</el-tag>
              </div>
            </div>
          </div>
          <el-empty v-if="exams.length === 0" description="近期无考试，祝学习愉快" />
        </div>
      </el-tab-pane>
    </el-tabs>

    <!-- 导入课表对话框 -->
    <el-dialog v-model="importVisible" title="导入课表" width="680px" :close-on-click-modal="false" @close="resetImport">
      <!-- 步骤1：选择文件 -->
      <div v-if="importStep === 'select'" class="import-step">
        <div style="display:flex;justify-content:space-between;align-items:center;margin-bottom:16px">
          <span style="color:#606266;font-size:14px">支持 .xlsx / .xls 课表格式</span>
          <el-button type="primary" link @click="downloadTemplate">
            <el-icon><Download /></el-icon> 下载导入模板
          </el-button>
        </div>
        <el-upload
          :auto-upload="false"
          :show-file-list="true"
          :limit="1"
          accept=".xlsx,.xls"
          :on-change="handleFileChange"
          :on-remove="handleFileRemove"
          drag>
          <div class="el-upload__text">将课表文件拖到此处，或<em>点击上传</em></div>
          <template #tip>
            <div class="el-upload__tip" style="color:#909399;font-size:12px;margin-top:8px">
              每行一门课程，列包含：课程名称、星期(1-7)、开始节数、结束节数、老师、地点、周数。周数支持"1-16"、"1-5、7-11单、12-16双"等格式
            </div>
          </template>
        </el-upload>
      </div>

      <!-- 步骤2：解析中 -->
      <div v-else-if="importStep === 'parsing'" class="import-parsing">
        <el-icon class="loading-icon" :size="48" color="#409eff"><Loading /></el-icon>
        <p style="margin-top:16px;color:#606266">{{ parsingText }}</p>
        <el-progress :percentage="parsingProgress" :show-text="false" style="margin-top:12px" />
      </div>

      <!-- 步骤3：预览确认 -->
      <div v-else-if="importStep === 'preview'" class="import-preview">
        <p style="margin-bottom:12px;color:#606266">已识别到 {{ parsedCourses.length }} 门课程，请确认后导入。导入将覆盖当前课表。</p>
        <el-table :data="parsedCourses" stripe size="small" max-height="320" style="width:100%">
          <el-table-column prop="courseName" label="课程" width="140" show-overflow-tooltip />
          <el-table-column prop="teacher" label="教师" width="80" />
          <el-table-column prop="classroom" label="教室" width="100" show-overflow-tooltip />
          <el-table-column label="时间" width="140">
            <template #default="{ row }">
              {{ getDayName(row.dayOfWeek) }} 第{{ row.startSection }}-{{ row.endSection }}节
            </template>
          </el-table-column>
          <el-table-column prop="weekRange" label="周次" width="90" show-overflow-tooltip />
          <el-table-column label="操作" width="70">
            <template #default="{ $index }">
              <el-button text type="danger" size="small" @click="parsedCourses.splice($index, 1)">删除</el-button>
            </template>
          </el-table-column>
        </el-table>
      </div>

      <template #footer>
        <el-button @click="importVisible = false">取消</el-button>
        <el-button v-if="importStep === 'select'" type="primary" :disabled="!importFile" @click="startParsing">
          开始解析
        </el-button>
        <el-button v-if="importStep === 'preview'" type="primary" :loading="importing" @click="confirmImport">
          确认导入（{{ parsedCourses.length }}门）
        </el-button>
      </template>
    </el-dialog>

    <!-- 课表设置弹窗 -->
    <el-dialog v-model="settingsVisible" title="课表设置" width="600px">
      <el-form label-width="100px">
        <el-form-item label="学期开始日期">
          <el-date-picker v-model="settingsForm.semesterStart" type="date"
            value-format="YYYY-MM-DD" placeholder="选择第一周周一" style="width:200px" />
          <span style="margin-left:12px;color:#909399;font-size:12px">第一周的周一</span>
        </el-form-item>
        <el-divider content-position="left">节次时间设置</el-divider>
        <el-form-item label="统一设置">
          <div class="unified-time-row">
            <span style="font-size:13px;color:#606266">首节开始</span>
            <el-time-picker v-model="unifiedStart" format="HH:mm" value-format="HH:mm"
              placeholder="08:00" style="width:110px" />
            <span style="font-size:13px;color:#606266">每节</span>
            <el-input-number v-model="unifiedDuration" :min="30" :max="120" :step="5"
              size="small" style="width:100px" controls-position="right" />
            <span style="font-size:13px;color:#606266">分钟，课间</span>
            <el-input-number v-model="unifiedBreak" :min="0" :max="60" :step="5"
              size="small" style="width:100px" controls-position="right" />
            <span style="font-size:13px;color:#606266">分钟</span>
            <el-button type="primary" size="small" @click="applyUnifiedTime">应用到全部</el-button>
          </div>
        </el-form-item>
        <el-form-item label="逐节设置">
          <div class="section-time-grid">
            <div v-for="(t, idx) in settingsForm.sectionTimes" :key="idx" class="section-time-item">
              <span class="sec-label">第{{ idx + 1 }}节</span>
              <el-time-picker v-model="t.start" format="HH:mm" value-format="HH:mm"
                placeholder="开始" size="small" style="width:100px" />
              <span class="time-sep">~</span>
              <el-time-picker v-model="t.end" format="HH:mm" value-format="HH:mm"
                placeholder="结束" size="small" style="width:100px" />
            </div>
          </div>
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="settingsVisible = false">取消</el-button>
        <el-button type="primary" @click="saveSettings">保存设置</el-button>
      </template>
    </el-dialog>

    <!-- 课程详情弹窗 -->
    <el-dialog v-model="detailVisible" title="课程详情" width="500px">
      <div v-if="currentCourse" class="course-detail">
        <el-descriptions :column="1" border>
          <el-descriptions-item label="课程名称">{{ currentCourse.courseName }}</el-descriptions-item>
          <el-descriptions-item label="授课教师">{{ currentCourse.teacher }}</el-descriptions-item>
          <el-descriptions-item label="上课时间">{{ getDayName(currentCourse.dayOfWeek) }} 第{{ currentCourse.startSection }}-{{ currentCourse.endSection }}节</el-descriptions-item>
          <el-descriptions-item label="上课教室">{{ currentCourse.classroom }}</el-descriptions-item>
          <el-descriptions-item label="学分">{{ currentCourse.credits }}</el-descriptions-item>
          <el-descriptions-item label="周次">{{ currentCourse.weekRange }}</el-descriptions-item>
          <el-descriptions-item label="课程简介">{{ currentCourse.description }}</el-descriptions-item>
        </el-descriptions>
      </div>
    </el-dialog>
  </div>
</template>

<script setup>
import { ref, onMounted, computed } from 'vue'
import { getCourses, getExams, batchSaveCourses, syncFromJwxt } from '@/api'
import { formatDate, getDayName } from '@/utils/format'
import { useUserStore } from '@/store/user'
import { ElMessage } from 'element-plus'
import * as XLSX from 'xlsx'

const userStore = useUserStore()
const activeTab = ref('courses')
const viewMode = ref('week')
const courses = ref([])
const exams = ref([])
const detailVisible = ref(false)
const currentCourse = ref(null)
const syncing = ref(false)

// ========== 课表导入 ==========
const importVisible = ref(false)
const importStep = ref('select')
const importFile = ref(null)
const parsedCourses = ref([])
const parsingText = ref('')
const parsingProgress = ref(0)
const importing = ref(false)

// ========== 课表编辑模式 ==========
const editMode = ref(false)
const editingCourses = ref([])
const savingEdit = ref(false)

// ========== 周次与学期设置 ==========
const SETTINGS_KEY = 'ai_hall_schedule_settings'
const TOTAL_WEEKS = 20
const defaultSectionTimes = [
  { start: '08:00', end: '08:45' },
  { start: '08:55', end: '09:40' },
  { start: '10:00', end: '10:45' },
  { start: '10:55', end: '11:40' },
  { start: '14:30', end: '15:15' },
  { start: '15:25', end: '16:10' },
  { start: '16:30', end: '17:15' },
  { start: '17:25', end: '18:10' },
  { start: '19:30', end: '20:15' },
  { start: '20:25', end: '21:10' },
  { start: '21:20', end: '22:05' },
  { start: '22:15', end: '23:00' },
]

function loadSettings() {
  try {
    const saved = localStorage.getItem(SETTINGS_KEY)
    if (saved) {
      const s = JSON.parse(saved)
      // 兼容旧版：如果保存的节次不足12节，用默认值补充
      let times = s.sectionTimes || []
      if (times.length < defaultSectionTimes.length) {
        times = [...times, ...defaultSectionTimes.slice(times.length)]
      }
      return {
        semesterStart: s.semesterStart || getDefaultSemesterStart(),
        sectionTimes: times,
      }
    }
  } catch {}
  return {
    semesterStart: getDefaultSemesterStart(),
    sectionTimes: JSON.parse(JSON.stringify(defaultSectionTimes)),
  }
}

function getDefaultSemesterStart() {
  // 默认：当前日期所在周的周一
  const now = new Date()
  const day = now.getDay() || 7
  const monday = new Date(now)
  monday.setDate(now.getDate() - (day - 1))
  return formatDate(monday)
}

const settings = ref(loadSettings())
const currentWeek = ref(calculateCurrentWeek())
const settingsVisible = ref(false)
const settingsForm = ref({ semesterStart: '', sectionTimes: [] })
const unifiedStart = ref('08:00')
const unifiedDuration = ref(45)
const unifiedBreak = ref(10)

function calculateCurrentWeek() {
  const start = new Date(settings.value.semesterStart + 'T00:00:00')
  const now = new Date()
  const diff = Math.floor((now - start) / (7 * 24 * 60 * 60 * 1000))
  const week = diff + 1
  return Math.max(1, Math.min(TOTAL_WEEKS, week))
}

function prevWeek() {
  if (currentWeek.value > 1) currentWeek.value--
}
function nextWeek() {
  if (currentWeek.value < TOTAL_WEEKS) currentWeek.value++
}
function goToCurrentWeek() {
  currentWeek.value = calculateCurrentWeek()
}

function openSettings() {
  settingsForm.value = {
    semesterStart: settings.value.semesterStart,
    sectionTimes: JSON.parse(JSON.stringify(settings.value.sectionTimes)),
  }
  settingsVisible.value = true
}

function saveSettings() {
  settings.value.semesterStart = settingsForm.value.semesterStart
  settings.value.sectionTimes = JSON.parse(JSON.stringify(settingsForm.value.sectionTimes))
  localStorage.setItem(SETTINGS_KEY, JSON.stringify(settings.value))
  currentWeek.value = calculateCurrentWeek()
  settingsVisible.value = false
  ElMessage.success('课表设置已保存')
}

function applyUnifiedTime() {
  const start = unifiedStart.value
  const duration = parseInt(unifiedDuration.value) || 45
  const breakTime = parseInt(unifiedBreak.value) || 10
  if (!start || !/^\d{2}:\d{2}$/.test(start)) {
    ElMessage.warning('请输入正确的开始时间，如 08:00')
    return
  }
  const [sh, sm] = start.split(':').map(Number)
  let totalMinutes = sh * 60 + sm
  const newTimes = []
  for (let i = 0; i < 8; i++) {
    const sH = Math.floor(totalMinutes / 60)
    const sM = totalMinutes % 60
    totalMinutes += duration
    const eH = Math.floor(totalMinutes / 60)
    const eM = totalMinutes % 60
    newTimes.push({
      start: `${String(sH).padStart(2, '0')}:${String(sM).padStart(2, '0')}`,
      end: `${String(eH).padStart(2, '0')}:${String(eM).padStart(2, '0')}`,
    })
    totalMinutes += breakTime
  }
  settingsForm.value.sectionTimes = newTimes
  ElMessage.success('已统一设置 8 节课的时间')
}

const today = computed(() => {
  const d = new Date().getDay()
  return d === 0 ? 7 : d
})

function enterEditMode() {
  editingCourses.value = JSON.parse(JSON.stringify(courses.value))
  viewMode.value = 'list'
  editMode.value = true
}

function cancelEdit() {
  editMode.value = false
  editingCourses.value = []
}

function addCourse() {
  editingCourses.value.push({
    courseName: '',
    teacher: '',
    classroom: '',
    credits: '',
    dayOfWeek: 1,
    startSection: 1,
    endSection: 1,
    weekRange: '',
    description: ''
  })
}

function removeCourse(index) {
  editingCourses.value.splice(index, 1)
}

async function saveEdit() {
  for (let i = 0; i < editingCourses.value.length; i++) {
    const c = editingCourses.value[i]
    if (!c.courseName || !c.courseName.trim()) {
      ElMessage.warning(`第${i + 1} 行课程名称不能为空`)
      return
    }
    if (c.dayOfWeek < 1 || c.dayOfWeek > 7) {
      ElMessage.warning(`第${i + 1} 行星期必须在 1-7 之间`)
      return
    }
    if (c.startSection < 1 || c.startSection > 12) {
      ElMessage.warning(`第${i + 1} 行开始节次必须在 1-12 之间`)
      return
    }
    if (c.endSection < c.startSection || c.endSection > 12) {
      ElMessage.warning(`第${i + 1} 行结束节次必须不小于开始节次且不超过12`)
      return
    }
  }
  savingEdit.value = true
  try {
    await batchSaveCourses(editingCourses.value)
    ElMessage.success('课表已保存')
    editMode.value = false
    await loadCourses()
  } catch (e) {
    const msg = e.response?.data?.message || e.message
    ElMessage.error(msg && msg !== 'Failed to fetch' ? msg : '保存时出现问题，请稍后重试')
  } finally {
    savingEdit.value = false
  }
}

function openImportDialog() {
  if (!userStore.isLoggedIn) {
    ElMessage.warning('请先登录后再导入课表')
    return
  }
  importVisible.value = true
}

function resetImport() {
  importStep.value = 'select'
  importFile.value = null
  parsedCourses.value = []
  parsingText.value = ''
  parsingProgress.value = 0
  importing.value = false
}

function handleFileChange(file) {
  importFile.value = file.raw
}

function handleFileRemove() {
  importFile.value = null
}

async function startParsing() {
  if (!importFile.value) return
  importStep.value = 'parsing'
  parsingProgress.value = 10
  try {
    parsingText.value = '正在解析 Excel 课表...'
    parsingProgress.value = 40
    const result = await parseExcel(importFile.value)
    parsingProgress.value = 100
    if (!result || result.length === 0) {
      ElMessage.warning('未识别到课程，请检查文件格式或使用导入模板')
      importStep.value = 'select'
      return
    }
    parsedCourses.value = result
    importStep.value = 'preview'
  } catch (e) {
    console.error('[课表导入] 解析失败:', e)
    ElMessage.error('解析失败：' + (e.message || '未知错误') + '，请检查文件格式后重试')
    importStep.value = 'select'
  }
}

async function confirmImport() {
  importing.value = true
  try {
    await batchSaveCourses(parsedCourses.value)
    ElMessage.success(`成功导入 ${parsedCourses.value.length} 门课程`)
    importVisible.value = false
    await loadCourses()
  } catch (e) {
    const msg = e.response?.data?.message || e.message
    ElMessage.error(msg && msg !== 'Failed to fetch' ? msg : '导入失败，请稍后重试')
  } finally {
    importing.value = false
  }
}

// 下载导入模板
function downloadTemplate() {
  const headers = ['课程名称', '星期', '开始节数', '结束节数', '老师', '地点', '周数']
  const sampleData = [
    headers,
    ['高等数学', '1', '1', '2', '小明', '逸夫楼201', '1-5、7-11单、12-16双'],
    ['线性代数', '2', '3', '4', '小红', '理工楼110', '1-16'],
    ['大学英语', '3', '5', '6', '小刚', '文成楼125', '2、5、8'],
    ['程序设计', '4', '7', '8', '小芳', '计算机楼301', '1-16'],
  ]
  const ws = XLSX.utils.aoa_to_sheet(sampleData)
  // 设置列宽
  ws['!cols'] = [
    { wch: 16 }, { wch: 8 }, { wch: 10 }, { wch: 10 },
    { wch: 10 }, { wch: 14 }, { wch: 24 },
  ]
  const wb = XLSX.utils.book_new()
  XLSX.utils.book_append_sheet(wb, ws, '课表')
  XLSX.writeFile(wb, '课表导入模板.xlsx')
  ElMessage.success('模板已下载，请按格式填写后导入')
}

// Excel 解析：自动检测网格格式 or 一行一课格式
async function parseExcel(file) {
  const buffer = await file.arrayBuffer()
  const workbook = XLSX.read(buffer, { type: 'array' })
  const sheet = workbook.Sheets[workbook.SheetNames[0]]
  if (!sheet || !sheet['!ref']) {
    throw new Error('Excel 文件为空或格式不正确')
  }

  const range = XLSX.utils.decode_range(sheet['!ref'])
  const dayHeaderCells = []
  const dayPattern = /(星期|周|礼拜)[一二三四五六日天1234567]/

  for (let R = range.s.r; R <= range.e.r; R++) {
    for (let C = range.s.c; C <= range.e.c; C++) {
      const cell = sheet[XLSX.utils.encode_cell({ r: R, c: C })]
      if (!cell || cell.v === undefined || cell.v === null) continue
      const str = String(cell.v).trim()
      if (!str) continue
      if (dayPattern.test(str)) dayHeaderCells.push({ str, R, C })
    }
  }

  console.log('[Excel解析] 检测到星期表头:', dayHeaderCells.map(h => `${h.str}@行${h.R}列${h.C}`))

  // 尝试网格课表解析
  if (dayHeaderCells.length >= 2) {
    try {
      const gridCourses = parseExcelGrid(sheet, range, dayHeaderCells)
      console.log('[Excel解析] 网格格式识别课程数:', gridCourses.length)
      if (gridCourses.length > 0) {
        const merged = mergeSameCourses(gridCourses)
        console.log('[Excel解析] 网格合并后课程数:', merged.length)
        return merged
      }
    } catch (e) {
      console.warn('[Excel解析] 网格解析异常，回退到一行一课格式', e.message)
    }
  }

  // 一行一课格式
  const rows = XLSX.utils.sheet_to_json(sheet, { defval: '' })
  console.log('[Excel解析] 一行一课数据行数:', rows.length)
  if (!rows || rows.length === 0) {
    throw new Error('Excel 中没有数据行，请检查文件内容')
  }

  const colMap = {
    courseName: ['课程名', '课程名称', '课程', 'courseName', 'course', 'name', '科目', '课程全称'],
    teacher: ['教师', '老师', '授课教师', 'teacher', 'instructor', '任课教师'],
    classroom: ['教室', '上课地点', '地点', 'classroom', 'location', 'room'],
    dayOfWeek: ['星期', '周几', '星期几', 'dayOfWeek', 'day', 'weekday', '上课星期'],
    startSection: ['开始节次', '起始节次', '开始节', '开始节数', '起始节数', 'startSection', 'start', '节次开始', '起始节次', '起始节'],
    endSection: ['结束节次', '终止节次', '结束节', '结束节数', '终止节数', 'endSection', 'end', '节次结束', '终止节'],
    weekRange: ['周次', '上课周次', '周数', '上课周数', 'weekRange', 'weeks', '周', '上课周'],
    credits: ['学分', 'credits', 'credit']
  }

  const headers = Object.keys(rows[0])
  console.log('[Excel解析] 列名:', headers)
  const fieldMap = {}
  for (const [field, aliases] of Object.entries(colMap)) {
    for (const h of headers) {
      if (aliases.some(a => h.toLowerCase().includes(a.toLowerCase()))) {
        fieldMap[field] = h
        break
      }
    }
  }
  console.log('[Excel解析] 字段映射:', fieldMap)

  if (!fieldMap.courseName) {
    throw new Error('未找到课程名列，请确认Excel 包含"课程名/课程名称"列')
  }

  const dayMap = { '一': 1, '二': 2, '三': 3, '四': 4, '五': 5, '六': 6, '日': 7, '天': 7, '1': 1, '2': 2, '3': 3, '4': 4, '5': 5, '6': 6, '7': 7 }

  const result = rows.map(row => {
    const course = {}
    for (const [field, col] of Object.entries(fieldMap)) {
      course[field] = row[col]
    }
    if (course.dayOfWeek !== undefined) {
      const s = String(course.dayOfWeek)
      for (const [k, v] of Object.entries(dayMap)) {
        if (s.includes(k)) { course.dayOfWeek = v; break }
      }
    }
    if (course.startSection !== undefined) course.startSection = parseInt(course.startSection) || 1
    if (course.endSection !== undefined) course.endSection = parseInt(course.endSection) || course.startSection || 1
    if (!course.courseName) return null
    return course
  }).filter(Boolean)

  // 合并同名同时间的课程（同一门课在不同周次可能不同教室）
  const merged = mergeSameCourses(result)
  console.log('[Excel解析] 识别课程数:', result.length, '合并后:', merged.length)
  return merged
}

// 合并同名、同星期、同节次范围的课程为同一门课
function mergeSameCourses(courses) {
  const map = new Map()
  for (const c of courses) {
    const key = `${c.courseName}_${c.dayOfWeek}_${c.startSection}_${c.endSection}`
    if (map.has(key)) {
      const existing = map.get(key)
      // 合并周次
      if (c.weekRange && !existing.weekRange.includes(c.weekRange)) {
        existing.weekRange = existing.weekRange ? `${existing.weekRange},${c.weekRange}` : c.weekRange
      }
      // 合并教室（不同教室用/分隔）
      if (c.classroom && c.classroom !== existing.classroom && !existing.classroom.includes(c.classroom)) {
        existing.classroom = `${existing.classroom}/${c.classroom}`
      }
      // 合并老师
      if (c.teacher && c.teacher !== existing.teacher && !existing.teacher.includes(c.teacher)) {
        existing.teacher = `${existing.teacher}/${c.teacher}`
      }
    } else {
      map.set(key, { ...c })
    }
  }
  return [...map.values()]
}

// 专用：Excel 网格课表解析
function parseExcelGrid(sheet, range, dayHeaderCells) {
  const rowCounts = {}
  for (const h of dayHeaderCells) {
    rowCounts[h.R] = (rowCounts[h.R] || 0) + 1
  }
  let headerRow = -1, maxCount = 0
  for (const [r, c] of Object.entries(rowCounts)) {
    if (c > maxCount) { maxCount = c; headerRow = parseInt(r) }
  }
  if (headerRow < 0) return []

  const dayNameToNum = {
    '星期一':1,'星期二':2,'星期三':3,'星期四':4,'星期五':5,'星期六':6,'星期日':7,'星期天':7,
    '周一':1,'周二':2,'周三':3,'周四':4,'周五':5,'周六':6,'周日':7,
    '礼拜一':1,'礼拜二':2,'礼拜三':3,'礼拜四':4,'礼拜五':5,'礼拜六':6,'礼拜日':7,'礼拜天':7
  }
  const colToDay = {}
  for (const h of dayHeaderCells) {
    if (h.R === headerRow && dayNameToNum[h.str]) {
      colToDay[h.C] = dayNameToNum[h.str]
    }
  }
  console.log('[Excel网格解析] 表头行:', headerRow, '列映射:', colToDay)

  const courses = []
  const seen = new Set()

  for (const [colStr, dayNum] of Object.entries(colToDay)) {
    const col = parseInt(colStr)
    for (let R = headerRow + 1; R <= range.e.r; R++) {
      const cell = sheet[XLSX.utils.encode_cell({ r: R, c: col })]
      if (!cell || cell.v === undefined || cell.v === null) continue
      const text = String(cell.v).trim()
      if (!text) continue
      const lines = text.split('\n').map(l => l.trim()).filter(Boolean)
      if (lines.length < 2) continue

      const course = {
        courseName: lines[0],
        dayOfWeek: dayNum,
        teacher: '',
        classroom: '',
        weekRange: '',
        startSection: 0,
        endSection: 0,
        credits: ''
      }

      for (let i = 1; i < lines.length; i++) {
        const line = lines[i]
        const secMatch = line.match(/\((\d+)[-~～至](\d+)节\)/)
        if (secMatch) {
          course.startSection = parseInt(secMatch[1])
          course.endSection = parseInt(secMatch[2])
        }
        const weekMatch = line.match(/\(\d+[-~～至]\d+节\)\s*(.+)$/)
        if (weekMatch) {
          course.weekRange = weekMatch[1].trim()
        }
        const creditMatch = line.match(/([\d.]+)\s*学分/)
        if (creditMatch) {
          course.credits = creditMatch[1]
        }
      }

      const infoLines = lines.slice(1).filter(l => !/\(\d+[-~～至]\d+节\)/.test(l) && !/[\d,\-~～至]+周/.test(l))
      if (infoLines.length >= 2) {
        course.classroom = infoLines[infoLines.length - 2]
        course.teacher = infoLines[infoLines.length - 1]
      } else if (infoLines.length === 1) {
        const line = infoLines[0]
        if (/\d/.test(line)) {
          course.classroom = line
        } else {
          course.teacher = line
        }
      }

      // 从节次列获取节次
      if (course.startSection === 0) {
        for (let C = 0; C < col; C++) {
          const hcell = sheet[XLSX.utils.encode_cell({ r: headerRow, c: C })]
          if (hcell && /节次/.test(String(hcell.v))) {
            const secCell = sheet[XLSX.utils.encode_cell({ r: R, c: C })]
            if (secCell) {
              const sec = parseInt(secCell.v)
              if (sec) {
                course.startSection = sec
                course.endSection = sec
              }
            }
            break
          }
        }
      }

      if (course.courseName && course.startSection > 0) {
        const key = `${course.courseName}|${course.dayOfWeek}|${course.startSection}-${course.endSection}`
        if (!seen.has(key)) {
          seen.add(key)
          courses.push(course)
        }
      }
    }
  }
  console.log('[Excel网格解析] 课程列表:', courses.map(c => c.courseName + ' 周' + c.dayOfWeek + ' ' + c.startSection + '-' + c.endSection + '节 ' + c.teacher + ' ' + c.classroom))
  return courses
}

async function loadCourses() {
  try {
    const res = await getCourses()
    const raw = res.data || []
    courses.value = mergeSameCourses(raw)
  } catch (e) {
    console.warn('[课表] 加载失败:', e.message)
    courses.value = []
  }
}

async function loadExams() {
  try {
    const res = await getExams()
    exams.value = res.data || []
  } catch (e) {
    console.warn('[考试] 加载失败:', e.message)
    exams.value = []
  }
}

async function handleSyncJwxt() {
  if (!userStore.isLoggedIn) {
    ElMessage.warning('请先登录后再同步')
    return
  }
  syncing.value = true
  try {
    const res = await syncFromJwxt()
    ElMessage.success(res.message || `同步成功：课表 ${res.data?.courseCount || 0} 门，考试 ${res.data?.examCount || 0} 场`)
    await Promise.all([loadCourses(), loadExams()])
  } catch (e) {
    const msg = e.response?.data?.message || e.message || '同步失败'
    ElMessage.error(msg && msg !== 'Failed to fetch' ? msg : '同步失败，请稍后重试')
  } finally {
    syncing.value = false
  }
}

const weekDays = computed(() => {
  return [1, 2, 3, 4, 5, 6, 7].map(day => ({
    day,
    name: getDayName(day),
    date: getWeekDate(day),
  }))
})

const sections = [1, 2, 3, 4, 5, 6, 7, 8, 9, 10, 11, 12]

const colors = [
  'linear-gradient(135deg, #667eea, #764ba2)',
  'linear-gradient(135deg, #f093fb, #f5576c)',
  'linear-gradient(135deg, #4facfe, #00f2fe)',
  'linear-gradient(135deg, #43e97b, #38f9d7)',
  'linear-gradient(135deg, #fa709a, #fee140)',
  'linear-gradient(135deg, #30cfd0, #330867)',
  'linear-gradient(135deg, #ff6b6b, #ee5a24)',
  'linear-gradient(135deg, #a55eea, #8854d0)',
  'linear-gradient(135deg, #26de81, #20bf6b)',
  'linear-gradient(135deg, #fd9644, #fa8231)',
  'linear-gradient(135deg, #45aaf2, #2d98da)',
  'linear-gradient(135deg, #fc5c65, #eb3b5a)',
]

function getWeekDate(day) {
  const start = new Date(settings.value.semesterStart + 'T00:00:00')
  const offsetDays = (currentWeek.value - 1) * 7 + (day - 1)
  const d = new Date(start)
  d.setDate(start.getDate() + offsetDays)
  return `${d.getMonth() + 1}/${d.getDate()}`
}

function getSectionTime(sec) {
  const t = settings.value.sectionTimes[sec - 1]
  return t ? `${t.start}-${t.end}` : ''
}

function getCoursesAt(day, sec) {
  return courses.value.filter(c => c.dayOfWeek === day && sec >= c.startSection && sec <= c.endSection)
}

// 解析周次范围，返回该课程上课的周次集合
function parseWeekRange(weekRange) {
  if (!weekRange || !weekRange.trim()) return null // null表示全周
  // 先处理括号形式的单/双周标记：(单)→单，(双)→双；去掉"第"字和"周"字
  let text = weekRange.replace(/\(单\)/g, '单').replace(/\(双\)/g, '双')
  text = text.replace(/第/g, '').replace(/周/g, '').trim()
  if (!text) return null
  const weeks = new Set()

  // 全局单周/双周（仅当整个文本就是"单"或"双"时）
  if (/^单$/.test(text)) {
    for (let w = 1; w <= TOTAL_WEEKS; w += 2) weeks.add(w)
    return weeks
  }
  if (/^双$/.test(text)) {
    for (let w = 2; w <= TOTAL_WEEKS; w += 2) weeks.add(w)
    return weeks
  }

  // 处理逗号/顿号分隔的段，如 "1,3,5-8"、"1-5、7-11单、12-16双"
  const parts = text.split(/[,，、]/)
  for (const part of parts) {
    const p = part.trim()
    if (!p) continue

    // 检查段末是否有单/双后缀
    let isOdd = false, isEven = false
    let rangeText = p
    if (p.endsWith('单')) {
      isOdd = true
      rangeText = p.slice(0, -1).trim()
    } else if (p.endsWith('双')) {
      isEven = true
      rangeText = p.slice(0, -1).trim()
    }

    const rangeMatch = rangeText.match(/(\d+)\s*[-~至]\s*(\d+)/)
    if (rangeMatch) {
      const start = parseInt(rangeMatch[1])
      const end = parseInt(rangeMatch[2])
      for (let w = start; w <= end; w++) {
        if (isOdd && w % 2 === 0) continue
        if (isEven && w % 2 === 1) continue
        weeks.add(w)
      }
    } else {
      const single = parseInt(rangeText)
      if (!isNaN(single)) weeks.add(single)
    }
  }
  return weeks.size > 0 ? weeks : null
}

// 判断课程在当前周是否有课
function isCourseThisWeek(course) {
  const weeks = parseWeekRange(course.weekRange)
  if (!weeks) return true // 无周次信息默认全周有课
  return weeks.has(currentWeek.value)
}

// 获取当前节次应显示的课程：仅显示当前周有课的课程，当前周没课则不显示
function getActiveCoursesAt(day, sec) {
  const all = getCoursesAt(day, sec)
  return all.filter(c => isCourseThisWeek(c))
}

function getCourseColor(courseName) {
  // 用课程名称的字符编码哈希取色，确保同一门课不同时间段颜色一致
  let hash = 0
  for (let i = 0; i < courseName.length; i++) {
    hash = (hash * 31 + courseName.charCodeAt(i)) >>> 0
  }
  return colors[hash % colors.length]
}

function showCourseDetail(course) {
  currentCourse.value = course
  detailVisible.value = true
}

function getMonth(t) { return t ? new Date(t).getMonth() + 1 : '' }
function getDay(t) { return t ? new Date(t).getDate() : '' }

function formatDateTime(t) {
  if (!t) return ''
  const d = new Date(t)
  return `${d.getMonth() + 1}月${d.getDate()}日 ${String(d.getHours()).padStart(2, '0')}:${String(d.getMinutes()).padStart(2, '0')}`
}

function isComing(t) {
  if (!t) return false
  const diff = new Date(t) - new Date()
  return diff > 0 && diff < 7 * 24 * 60 * 60 * 1000
}

function getCountdown(t) {
  const diff = new Date(t) - new Date()
  const days = Math.ceil(diff / (24 * 60 * 60 * 1000))
  return `还有${days}天`
}

onMounted(async () => {
  await Promise.all([loadCourses(), loadExams()])
})
</script>

<style scoped>
.schedule-page {
  display: flex;
  flex-direction: column;
  gap: 20px;
}

.page-header h2 {
  font-size: 22px;
  font-weight: 700;
  color: #303133;
  display: flex;
  align-items: center;
  gap: 10px;
  margin: 0 0 6px 0;
}

.page-header p {
  color: #909399;
  font-size: 14px;
  margin: 0;
}

.toolbar {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 16px;
}

.toolbar-right {
  display: flex;
  align-items: center;
}

.week-selector {
  display: flex;
  align-items: center;
  gap: 12px;
  margin-left: 16px;
}

.week-label {
  font-size: 15px;
  font-weight: 600;
  color: #303133;
  cursor: pointer;
  min-width: 80px;
  text-align: center;
  user-select: none;
}

.week-label:hover {
  color: #409eff;
}

.unified-time-row {
  display: flex;
  align-items: center;
  gap: 8px;
  flex-wrap: wrap;
}

.section-time-grid {
  display: grid;
  grid-template-columns: repeat(2, 1fr);
  gap: 8px;
  width: 100%;
  max-height: 320px;
  overflow-y: auto;
  padding-right: 4px;
}

.section-time-item {
  display: flex;
  align-items: center;
  gap: 4px;
  min-width: 0;
}

.sec-label {
  font-size: 12px;
  color: #606266;
  width: 42px;
  flex-shrink: 0;
}

.time-sep {
  color: #909399;
  font-size: 12px;
  flex-shrink: 0;
}

.section-time-item :deep(.el-time-picker) {
  width: 85px;
}

.section-time-item :deep(.el-input__wrapper) {
  padding: 0 8px;
}

.section-time-item :deep(.el-input__inner) {
  font-size: 12px;
}

.empty-schedule {
  background: #fff;
  border-radius: 12px;
  padding: 48px 24px;
  border: 1px solid #ebeef5;
  text-align: center;
}

.import-step, .import-parsing, .import-preview {
  min-height: 200px;
}

.import-parsing {
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  padding: 32px;
}

.loading-icon {
  animation: rotate 1.5s linear infinite;
}

@keyframes rotate {
  from { transform: rotate(0deg); }
  to { transform: rotate(360deg); }
}

.timetable {
  background: #fff;
  border-radius: 12px;
  overflow: hidden;
  border: 1px solid #ebeef5;
}

.timetable-header, .time-row {
  display: grid;
  grid-template-columns: 100px repeat(7, 1fr);
}

.timetable-header {
  background: linear-gradient(135deg, #1a1a2e, #16213e);
  color: #fff;
}

.timetable-header > div {
  padding: 12px 8px;
  text-align: center;
  font-weight: 600;
  font-size: 14px;
  border-right: 1px solid rgba(255,255,255,0.1);
}

.timetable-header .date {
  display: block;
  font-size: 11px;
  font-weight: 400;
  opacity: 0.7;
  margin-top: 2px;
}

.timetable-header .today {
  background: rgba(64, 158, 255, 0.3);
}

.time-row {
  border-bottom: 1px solid #ebeef5;
  min-height: 70px;
}

.time-row .time-col {
  background: #f5f7fa;
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  border-right: 1px solid #ebeef5;
  padding: 8px 4px;
}

.section-num {
  font-size: 13px;
  font-weight: 600;
  color: #303133;
}

.section-time {
  font-size: 10px;
  color: #909399;
  margin-top: 2px;
}

.day-col {
  border-right: 1px solid #f0f2f5;
  padding: 4px;
  position: relative;
  display: flex;
  flex-direction: column;
  gap: 2px;
  min-width: 0;
}

.course-block {
  border-radius: 6px;
  padding: 6px 8px;
  color: #fff;
  font-size: 11px;
  cursor: pointer;
  min-height: 60px;
  display: flex;
  flex-direction: column;
  justify-content: center;
  transition: transform 0.2s;
  width: 100%;
  max-width: 100%;
  box-sizing: border-box;
  flex-shrink: 0;
}

.course-block:hover {
  transform: scale(1.02);
}

.course-name {
  font-weight: 700;
  font-size: 12px;
  margin-bottom: 2px;
}

.course-room, .course-teacher {
  font-size: 10px;
  opacity: 0.9;
}

.course-list {
  background: #fff;
  border-radius: 12px;
  padding: 16px;
}

.exam-list {
  display: flex;
  flex-direction: column;
  gap: 16px;
}

.exam-card {
  display: flex;
  gap: 20px;
  background: #fff;
  border-radius: 12px;
  padding: 20px;
  border: 1px solid #ebeef5;
}

.exam-card.ended {
  opacity: 0.6;
}

.exam-date {
  width: 80px;
  height: 80px;
  background: linear-gradient(135deg, #409eff, #66b1ff);
  border-radius: 12px;
  color: #fff;
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  flex-shrink: 0;
}

.exam-month {
  font-size: 14px;
  opacity: 0.9;
}

.exam-day {
  font-size: 32px;
  font-weight: 800;
  line-height: 1;
}

.exam-info {
  flex: 1;
}

.exam-info h4 {
  font-size: 18px;
  font-weight: 700;
  color: #303133;
  margin: 0 0 10px 0;
}

.exam-meta {
  display: flex;
  flex-wrap: wrap;
  gap: 16px;
  margin-bottom: 10px;
}

.exam-meta span {
  display: flex;
  align-items: center;
  gap: 4px;
  font-size: 13px;
  color: #606266;
}

.exam-tags {
  display: flex;
  gap: 8px;
}

/* ========== 移动端响应式 ========== */
@media (max-width: 768px) {
  .page-header h2 {
    font-size: 18px;
  }
  .page-header p {
    font-size: 12px;
  }
  .toolbar {
    flex-direction: column;
    align-items: flex-start;
    gap: 12px;
  }
  .toolbar-right {
    width: 100%;
    flex-wrap: wrap;
  }
  .week-selector {
    margin-left: 0;
    width: 100%;
    justify-content: center;
  }
  .section-time-grid {
    grid-template-columns: 1fr;
  }
  .unified-time-row {
    gap: 6px;
  }
  .empty-schedule {
    padding: 32px 16px;
  }
  .timetable-header, .time-row {
    grid-template-columns: 60px repeat(7, 1fr);
  }
  .timetable-header > div {
    padding: 8px 2px;
    font-size: 12px;
  }
  .timetable-header .date {
    font-size: 10px;
  }
  .time-row {
    min-height: 56px;
  }
  .time-row .time-col {
    padding: 6px 2px;
  }
  .section-num {
    font-size: 11px;
  }
  .section-time {
    font-size: 9px;
  }
  .day-col {
    padding: 2px;
  }
  .course-block {
    padding: 4px 4px;
    font-size: 10px;
    min-height: 48px;
  }
  .course-name {
    font-size: 11px;
  }
  .course-room, .course-teacher {
    font-size: 9px;
  }
  .course-list {
    padding: 12px 8px;
  }
  .exam-card {
    flex-direction: column;
    gap: 12px;
    padding: 16px;
  }
  .exam-date {
    width: 64px;
    height: 64px;
  }
  .exam-day {
    font-size: 26px;
  }
  .exam-info h4 {
    font-size: 16px;
  }
  .exam-meta {
    gap: 10px;
  }
  .exam-meta span {
    font-size: 12px;
  }
}

@media (max-width: 480px) {
  .timetable-header, .time-row {
    grid-template-columns: 50px repeat(7, 1fr);
  }
  .timetable-header > div {
    font-size: 11px;
    padding: 6px 1px;
  }
  .section-num {
    font-size: 10px;
  }
  .section-time {
    display: none;
  }
  .course-block {
    font-size: 9px;
    padding: 3px 2px;
  }
  .course-name {
    font-size: 10px;
  }
  .course-room, .course-teacher {
    font-size: 8px;
  }
}
</style>
