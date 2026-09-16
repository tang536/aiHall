# Ollama 模型测速脚本
# 用法：在项目根目录执行
#   powershell -ExecutionPolicy Bypass -File .\benchmark-model.ps1 -Model qwen2.5:3b
# 可分别对不同模型执行，对比速度
param(
    [Parameter(Mandatory=$true)][string]$Model,
    [string]$Question = "贫困认定需要准备哪些材料？办理流程是什么？",
    [string]$Ollama = "http://localhost:11434"
)

Write-Output "===================================================="
Write-Output "模型: $Model"
Write-Output "问题: $Question"
Write-Output "===================================================="

$body = @{ model = $Model; prompt = $Question; stream = $false } | ConvertTo-Json
$sw = [System.Diagnostics.Stopwatch]::StartNew()
try {
    $resp = Invoke-RestMethod -Uri "$Ollama/api/generate" -Method Post -Body $body -ContentType "application/json; charset=utf-8"
} catch {
    Write-Output "调用失败：$($_.Exception.Message)"
    Write-Output "请确认模型已下载：ollama pull $Model"
    exit 1
}
$sw.Stop()

Write-Output ("总耗时        : {0:N1} 秒" -f $sw.Elapsed.TotalSeconds)
Write-Output ("模型加载耗时  : {0:N1} 秒 (首次/常驻后为0)" -f ($resp.load_duration/1e9))
Write-Output ("提示词处理    : {0:N1} 秒" -f ($resp.prompt_eval_duration/1e9))
Write-Output ("生成 token 数 : {0}" -f $resp.eval_count)
Write-Output ("纯生成速度    : {0:N2} token/秒" -f ($resp.eval_count/($resp.eval_duration/1e9)))

Write-Output "`n------------ ollama ps (CPU/GPU 分配) ------------"
ollama ps

Write-Output "`n------------ 模型回答 ------------"
Write-Output $resp.response
Write-Output "===================================================="
