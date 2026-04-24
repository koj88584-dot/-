param(
    [string]$OutputDir = "C:\Users\20878\Desktop\hmdp-master\deliverables",
    [string]$PreviewDir = "C:\Users\20878\Desktop\hmdp-master\deliverables\ppt_preview"
)

$ErrorActionPreference = "Stop"

function Pt([double]$inch) { return [int][Math]::Round($inch * 72) }

function Color($hex) {
    $h = $hex.TrimStart("#")
    $r = [Convert]::ToInt32($h.Substring(0, 2), 16)
    $g = [Convert]::ToInt32($h.Substring(2, 2), 16)
    $b = [Convert]::ToInt32($h.Substring(4, 2), 16)
    return $r + ($g * 256) + ($b * 65536)
}

$Theme = @{
    Ink = "17211F"
    Text = "24302E"
    Muted = "6B7672"
    Paper = "F7F8F4"
    Card = "FFFFFF"
    Mint = "1BA784"
    Teal = "0E7C7B"
    Coral = "FF6B5C"
    Amber = "F2B84B"
    Line = "DDE5DE"
    SoftMint = "DDF3EA"
    SoftCoral = "FFE7E1"
    SoftAmber = "FFF2CF"
}

$Font = "Microsoft YaHei"
$TitleFont = "Microsoft YaHei UI"

New-Item -ItemType Directory -Path $OutputDir -Force | Out-Null
New-Item -ItemType Directory -Path $PreviewDir -Force | Out-Null

$ppt = $null
$deck = $null

function Set-Fill($shape, [string]$hex) {
    $shape.Fill.Visible = -1
    $shape.Fill.ForeColor.RGB = Color $hex
    $shape.Line.Visible = 0
}

function Set-Line($shape, [string]$hex, [double]$width = 1) {
    $shape.Line.Visible = -1
    $shape.Line.ForeColor.RGB = Color $hex
    $shape.Line.Weight = $width
}

function Add-Text($slide, [string]$text, [double]$x, [double]$y, [double]$w, [double]$h, [int]$size = 18, [string]$color = "24302E", [switch]$Bold, [int]$align = 1, [string]$fontName = $Font) {
    $box = $slide.Shapes.AddTextbox(1, (Pt $x), (Pt $y), (Pt $w), (Pt $h))
    $box.TextFrame2.TextRange.Text = $text
    $box.TextFrame2.MarginLeft = 0
    $box.TextFrame2.MarginRight = 0
    $box.TextFrame2.MarginTop = 0
    $box.TextFrame2.MarginBottom = 0
    $box.TextFrame2.WordWrap = -1
    $box.TextFrame2.TextRange.Font.Name = $fontName
    $box.TextFrame2.TextRange.Font.NameFarEast = $fontName
    $box.TextFrame2.TextRange.Font.Size = $size
    $box.TextFrame2.TextRange.Font.Fill.ForeColor.RGB = Color $color
    $box.TextFrame2.TextRange.Font.Bold = $(if ($Bold) { -1 } else { 0 })
    $box.TextFrame2.TextRange.ParagraphFormat.Alignment = $align
    return $box
}

function Add-Rect($slide, [double]$x, [double]$y, [double]$w, [double]$h, [string]$fill, [string]$line = "", [double]$lineWidth = 1, [switch]$Round) {
    $shapeType = $(if ($Round) { 5 } else { 1 })
    $shape = $slide.Shapes.AddShape($shapeType, (Pt $x), (Pt $y), (Pt $w), (Pt $h))
    Set-Fill $shape $fill
    if ($line -ne "") { Set-Line $shape $line $lineWidth }
    return $shape
}

function Add-Circle($slide, [double]$x, [double]$y, [double]$d, [string]$fill, [string]$line = "") {
    $shape = $slide.Shapes.AddShape(9, (Pt $x), (Pt $y), (Pt $d), (Pt $d))
    Set-Fill $shape $fill
    if ($line -ne "") { Set-Line $shape $line 1 }
    return $shape
}

function Add-Line($slide, [double]$x1, [double]$y1, [double]$x2, [double]$y2, [string]$color = "DDE5DE", [double]$width = 1.5, [switch]$Arrow) {
    $line = $slide.Shapes.AddLine((Pt $x1), (Pt $y1), (Pt $x2), (Pt $y2))
    $line.Line.ForeColor.RGB = Color $color
    $line.Line.Weight = $width
    if ($Arrow) { $line.Line.EndArrowheadStyle = 3 }
    return $line
}

function Add-Header($slide, [string]$title, [string]$kicker = "") {
    Add-Text $slide $title 0.55 0.34 8.4 0.52 24 $Theme.Ink -Bold 1 $TitleFont | Out-Null
    if ($kicker -ne "") {
        Add-Text $slide $kicker 0.57 0.9 5.4 0.24 9 $Theme.Muted 1 $Font | Out-Null
    }
    Add-Circle $slide 12.05 0.34 0.22 $Theme.Coral | Out-Null
    Add-Circle $slide 12.35 0.34 0.22 $Theme.Mint | Out-Null
    Add-Circle $slide 12.65 0.34 0.22 $Theme.Amber | Out-Null
}

function Add-Footer($slide, [int]$n) {
    Add-Text $slide ("周边优选通 | 中期答辩  " + $n.ToString("00")) 10.78 7.08 1.95 0.18 7 "8A938F" 1 $Font | Out-Null
}

function Add-Card($slide, [double]$x, [double]$y, [double]$w, [double]$h, [string]$title, [string]$body, [string]$accent = "1BA784") {
    Add-Rect $slide $x $y $w $h $Theme.Card $Theme.Line 1 -Round | Out-Null
    Add-Rect $slide $x $y 0.08 $h $accent "" 0 | Out-Null
    Add-Text $slide $title ($x + 0.22) ($y + 0.18) ($w - 0.38) 0.3 14 $Theme.Ink -Bold 1 $TitleFont | Out-Null
    Add-Text $slide $body ($x + 0.22) ($y + 0.58) ($w - 0.38) ($h - 0.72) 10.5 $Theme.Text 1 $Font | Out-Null
}

function Add-Stat($slide, [double]$x, [double]$y, [double]$w, [string]$num, [string]$label, [string]$accent) {
    Add-Rect $slide $x $y $w 1.12 $Theme.Card $Theme.Line 1 -Round | Out-Null
    Add-Text $slide $num ($x + 0.16) ($y + 0.18) ($w - 0.32) 0.42 25 $accent -Bold 2 $TitleFont | Out-Null
    Add-Text $slide $label ($x + 0.12) ($y + 0.72) ($w - 0.24) 0.24 9.5 $Theme.Muted 2 $Font | Out-Null
}

function Add-Progress($slide, [double]$x, [double]$y, [double]$w, [string]$label, [int]$pct, [string]$accent) {
    Add-Text $slide $label $x $y 2.5 0.22 10.5 $Theme.Text -Bold 1 $Font | Out-Null
    Add-Rect $slide ($x + 2.55) ($y + 0.04) $w 0.12 "E9EEE9" "" 0 -Round | Out-Null
    Add-Rect $slide ($x + 2.55) ($y + 0.04) ($w * $pct / 100.0) 0.12 $accent "" 0 -Round | Out-Null
    Add-Text $slide ($pct.ToString() + "%") ($x + 2.55 + $w + 0.15) ($y - 0.01) 0.5 0.2 9 $Theme.Muted 1 $Font | Out-Null
}

function Add-Node($slide, [string]$text, [double]$x, [double]$y, [double]$w, [double]$h, [string]$fill, [string]$line = "") {
    Add-Rect $slide $x $y $w $h $fill $(if ($line -ne "") { $line } else { $Theme.Line }) 1 -Round | Out-Null
    Add-Text $slide $text ($x + 0.08) ($y + 0.12) ($w - 0.16) ($h - 0.16) 10.5 $Theme.Text -Bold 2 $Font | Out-Null
}

try {
    $ppt = New-Object -ComObject PowerPoint.Application
    $ppt.Visible = -1
    $ppt.WindowState = 2
    $deck = $ppt.Presentations.Add()
    $deck.PageSetup.SlideWidth = Pt 13.333
    $deck.PageSetup.SlideHeight = Pt 7.5
    $deck.ApplyTheme("")

    $slideNo = 1

    # 1. Title
    $s = $deck.Slides.Add($slideNo, 12)
    $s.FollowMasterBackground = 0
    $s.Background.Fill.ForeColor.RGB = Color $Theme.Ink
    Add-Circle $s 9.6 -0.6 3.4 $Theme.Teal | Out-Null
    Add-Circle $s 11.2 4.9 2.0 $Theme.Coral | Out-Null
    Add-Rect $s 8.35 1.05 3.25 5.22 "F9FAF6" "" 0 -Round | Out-Null
    Add-Rect $s 8.63 1.42 2.7 0.42 $Theme.Mint "" 0 -Round | Out-Null
    Add-Rect $s 8.63 2.08 2.7 0.72 "EEF7F3" "" 0 -Round | Out-Null
    Add-Rect $s 8.63 3.03 1.2 0.76 $Theme.SoftCoral "" 0 -Round | Out-Null
    Add-Rect $s 10.12 3.03 1.2 0.76 $Theme.SoftAmber "" 0 -Round | Out-Null
    Add-Rect $s 8.63 4.12 2.7 1.16 "EAF0ED" "" 0 -Round | Out-Null
    Add-Circle $s 9.08 4.42 0.28 $Theme.Coral | Out-Null
    Add-Circle $s 10.08 4.62 0.22 $Theme.Mint | Out-Null
    Add-Circle $s 10.75 4.3 0.2 $Theme.Amber | Out-Null
    Add-Text $s "周边优选通" 0.75 1.25 5.6 0.72 34 "FFFFFF" -Bold 1 $TitleFont | Out-Null
    Add-Text $s "本地生活点评平台中期答辩" 0.78 2.05 6.7 0.46 21 "DDF3EA" -Bold 1 $TitleFont | Out-Null
    Add-Text $s "Spring Boot + Redis + MySQL + Vue/Element UI" 0.82 2.72 5.6 0.28 11 "BBD0CA" 1 $Font | Out-Null
    Add-Rect $s 0.82 3.45 4.9 1.34 "21302D" "3B4E49" 1 -Round | Out-Null
    Add-Text $s "当前阶段已完成账户、店铺、优惠券、社区内容、商家入驻、地图检索与 AI 助手等主流程，正在进入压测、部署和演示 polish 阶段。" 1.05 3.72 4.35 0.72 12 "F7F8F4" 1 $Font | Out-Null
    Add-Text $s "答辩人：XXX    指导教师：XXX    2026年4月" 0.82 6.7 5.8 0.24 10 "BBD0CA" 1 $Font | Out-Null

    # 2. Agenda
    $slideNo++
    $s = $deck.Slides.Add($slideNo, 12)
    $s.FollowMasterBackground = 0
    $s.Background.Fill.ForeColor.RGB = Color $Theme.Paper
    Add-Header $s "答辩目录" "从业务目标到阶段成果，再落到下一阶段计划"
    $items = @(
        @("01", "项目背景与目标", "本地生活发现、内容互动和优惠券交易闭环"),
        @("02", "需求与总体设计", "角色边界、系统架构、数据库与缓存设计"),
        @("03", "核心功能实现", "登录认证、店铺缓存、秒杀下单、内容社交"),
        @("04", "阶段成果与测试", "页面、接口、单测与质量保障"),
        @("05", "问题风险与计划", "压测、部署、安全配置与答辩演示")
    )
    $x = 0.78
    for ($i = 0; $i -lt $items.Count; $i++) {
        $y = 1.58 + $i * 0.92
        Add-Circle $s $x $y 0.48 $(if ($i % 2 -eq 0) { $Theme.Mint } else { $Theme.Coral }) | Out-Null
        Add-Text $s $items[$i][0] ($x + 0.07) ($y + 0.13) 0.34 0.18 10 "FFFFFF" -Bold 2 $TitleFont | Out-Null
        Add-Text $s $items[$i][1] 1.48 ($y + 0.02) 2.2 0.28 15 $Theme.Ink -Bold 1 $TitleFont | Out-Null
        Add-Text $s $items[$i][2] 3.82 ($y + 0.06) 6.5 0.24 11 $Theme.Muted 1 $Font | Out-Null
        if ($i -lt $items.Count - 1) { Add-Line $s ($x + 0.24) ($y + 0.48) ($x + 0.24) ($y + 0.9) "C9D5CF" 1.4 | Out-Null }
    }
    Add-Rect $s 10.7 1.45 1.55 4.7 $Theme.Ink "" 0 -Round | Out-Null
    Add-Text $s "中期答辩重点" 10.92 1.78 1.1 0.34 14 "FFFFFF" -Bold 2 $TitleFont | Out-Null
    Add-Text $s "讲清楚做了什么，为什么这样设计，以及后半程如何收口。" 10.96 2.45 1.02 2.6 13 "DDF3EA" -Bold 2 $Font | Out-Null
    Add-Footer $s $slideNo

    # 3. Background
    $slideNo++
    $s = $deck.Slides.Add($slideNo, 12)
    $s.FollowMasterBackground = 0
    $s.Background.Fill.ForeColor.RGB = Color $Theme.Paper
    Add-Header $s "项目背景与建设目标" "围绕本地生活场景，打通找店、看评价、领券和交易"
    Add-Card $s 0.72 1.42 3.58 1.45 "用户侧痛点" "信息分散，附近好店难发现；优惠券与订单入口割裂；收藏、浏览、关注等个性化能力不足。" $Theme.Coral
    Add-Card $s 4.92 1.42 3.58 1.45 "商家侧诉求" "需要自助入驻、认领门店、发布优惠券，并能查看券订单和核销结果。" $Theme.Mint
    Add-Card $s 9.12 1.42 3.45 1.45 "平台侧目标" "用审核、风控、缓存和异步化能力支撑可靠运营，同时保留扩展 AI/地图能力的接口。" $Theme.Amber
    Add-Rect $s 0.9 3.68 11.6 1.28 $Theme.Ink "" 0 -Round | Out-Null
    Add-Text $s "建设目标" 1.22 3.95 1.45 0.34 16 "FFFFFF" -Bold 1 $TitleFont | Out-Null
    Add-Text $s "形成「发现店铺 → 查看详情/评价 → 领取或秒杀优惠券 → 下单/支付/核销 → 内容分享与关系沉淀」的闭环。" 2.8 3.98 8.9 0.42 16 "DDF3EA" -Bold 1 $Font | Out-Null
    Add-Node $s "发现" 1.2 5.62 1.18 0.55 $Theme.SoftMint $Theme.Mint
    Add-Line $s 2.43 5.9 3.0 5.9 $Theme.Mint 2 -Arrow | Out-Null
    Add-Node $s "评价" 3.05 5.62 1.18 0.55 $Theme.SoftCoral $Theme.Coral
    Add-Line $s 4.28 5.9 4.85 5.9 $Theme.Coral 2 -Arrow | Out-Null
    Add-Node $s "领券" 4.9 5.62 1.18 0.55 $Theme.SoftAmber $Theme.Amber
    Add-Line $s 6.13 5.9 6.7 5.9 $Theme.Amber 2 -Arrow | Out-Null
    Add-Node $s "下单" 6.75 5.62 1.18 0.55 $Theme.SoftMint $Theme.Mint
    Add-Line $s 7.98 5.9 8.55 5.9 $Theme.Mint 2 -Arrow | Out-Null
    Add-Node $s "分享" 8.6 5.62 1.18 0.55 $Theme.SoftCoral $Theme.Coral
    Add-Line $s 9.83 5.9 10.4 5.9 $Theme.Coral 2 -Arrow | Out-Null
    Add-Node $s "复访" 10.45 5.62 1.18 0.55 $Theme.SoftAmber $Theme.Amber
    Add-Footer $s $slideNo

    # 4. Requirements
    $slideNo++
    $s = $deck.Slides.Add($slideNo, 12)
    $s.FollowMasterBackground = 0
    $s.Background.Fill.ForeColor.RGB = Color $Theme.Paper
    Add-Header $s "需求范围与角色边界" "以四类角色划分功能，降低权限与业务复杂度"
    $roles = @(
        @("普通用户", "注册登录、签到、店铺浏览、搜索、收藏、关注、发笔记、评论、领券与订单管理", $Theme.Mint),
        @("商家用户", "提交入驻/认领/新建门店申请，维护优惠券，查看并核销券订单", $Theme.Coral),
        @("管理员", "审核商家入驻、门店认领、新店创建，保障平台内容与交易可信", $Theme.Amber),
        @("系统服务", "Redis 缓存、限流、秒杀异步下单、地图 POI、DeepSeek 助手路由", $Theme.Teal)
    )
    for ($i = 0; $i -lt $roles.Count; $i++) {
        $x = 0.7 + ($i % 2) * 6.1
        $y = 1.42 + [Math]::Floor($i / 2) * 1.9
        Add-Card $s $x $y 5.45 1.46 $roles[$i][0] $roles[$i][1] $roles[$i][2]
    }
    Add-Rect $s 1.0 5.6 11.3 0.82 "EBF3EF" "C9DCD4" 1 -Round | Out-Null
    Add-Text $s "中期验收口径" 1.28 5.82 1.35 0.25 13 $Theme.Ink -Bold 1 $TitleFont | Out-Null
    Add-Text $s "主流程可演示、核心接口成型、数据库/缓存模型可解释，后续重点转向稳定性、安全性和部署交付。" 2.82 5.83 8.55 0.24 11.5 $Theme.Text 1 $Font | Out-Null
    Add-Footer $s $slideNo

    # 5. Architecture
    $slideNo++
    $s = $deck.Slides.Add($slideNo, 12)
    $s.FollowMasterBackground = 0
    $s.Background.Fill.ForeColor.RGB = Color $Theme.Paper
    Add-Header $s "总体架构设计" "前后端分离，后端以 Spring Boot 分层承载业务，Redis 支撑高频访问"
    Add-Node $s "Vue 2 + Element UI`n静态页面 28 个" 0.72 2.0 2.05 0.95 $Theme.SoftMint $Theme.Mint
    Add-Line $s 2.85 2.47 3.62 2.47 $Theme.Mint 2 -Arrow | Out-Null
    Add-Node $s "Controller`n21 个控制器 / 118 个接口映射" 3.68 1.52 2.25 0.82 $Theme.Card $Theme.Line
    Add-Node $s "Service`n业务编排 / 事务 / 规则校验" 3.68 2.62 2.25 0.82 $Theme.Card $Theme.Line
    Add-Node $s "Mapper`nMyBatis-Plus / SQL 映射" 3.68 3.72 2.25 0.82 $Theme.Card $Theme.Line
    Add-Line $s 5.98 2.04 6.72 2.04 $Theme.Coral 2 -Arrow | Out-Null
    Add-Line $s 5.98 3.04 6.72 3.04 $Theme.Coral 2 -Arrow | Out-Null
    Add-Line $s 5.98 4.14 6.72 4.14 $Theme.Coral 2 -Arrow | Out-Null
    Add-Node $s "MySQL`n用户/店铺/内容/券/订单" 6.78 1.58 2.0 0.88 $Theme.SoftAmber $Theme.Amber
    Add-Node $s "Redis`n会话、缓存、GEO、ZSet、Stream、限流" 6.78 2.9 2.0 1.02 $Theme.SoftCoral $Theme.Coral
    Add-Node $s "本地文件`n图片上传与静态资源" 6.78 4.36 2.0 0.72 $Theme.Card $Theme.Line
    Add-Node $s "高德地图 API`nPOI / 逆地理 / 周边搜索" 9.78 2.02 2.28 0.85 $Theme.SoftMint $Theme.Mint
    Add-Node $s "DeepSeek API`n对话生成 + 意图动作推荐" 9.78 3.28 2.28 0.85 $Theme.SoftCoral $Theme.Coral
    Add-Line $s 8.85 3.38 9.7 2.45 $Theme.Mint 1.6 -Arrow | Out-Null
    Add-Line $s 8.85 3.38 9.7 3.7 $Theme.Coral 1.6 -Arrow | Out-Null
    Add-Rect $s 0.86 5.72 11.45 0.48 $Theme.Ink "" 0 -Round | Out-Null
    Add-Text $s "横切能力：Token 刷新拦截器、登录鉴权、AOP 限流、全局异常处理、环境变量化配置" 1.06 5.88 10.9 0.18 10.5 "FFFFFF" -Bold 2 $Font | Out-Null
    Add-Footer $s $slideNo

    # 6. Data model
    $slideNo++
    $s = $deck.Slides.Add($slideNo, 12)
    $s.FollowMasterBackground = 0
    $s.Background.Fill.ForeColor.RGB = Color $Theme.Paper
    Add-Header $s "数据库与数据对象设计" "围绕用户、店铺、内容、优惠券和运营审核建立扩展模型"
    Add-Rect $s 5.35 2.55 2.3 1.0 $Theme.Ink "" 0 -Round | Out-Null
    Add-Text $s "HMDP 数据中心" 5.62 2.86 1.75 0.26 15 "FFFFFF" -Bold 2 $TitleFont | Out-Null
    $groups = @(
        @("用户域", "tb_user`ntb_user_info`ntb_user_role`ntb_privacy_setting", 0.72, 1.28, $Theme.SoftMint, $Theme.Mint),
        @("店铺域", "tb_shop`ntb_shop_type`ntb_shop_extend`ntb_shop_member", 0.72, 4.38, $Theme.SoftAmber, $Theme.Amber),
        @("内容域", "tb_blog`ntb_blog_comments`ntb_follow`ntb_favorites`ntb_browse_history", 9.62, 1.18, $Theme.SoftCoral, $Theme.Coral),
        @("交易域", "tb_voucher`ntb_seckill_voucher`ntb_voucher_order", 9.62, 4.38, $Theme.SoftMint, $Theme.Mint),
        @("审核域", "tb_merchant_application`ntb_shop_claim_application`ntb_shop_create_application", 4.42, 5.02, "EEF0F2", $Theme.Teal)
    )
    foreach ($g in $groups) {
        Add-Rect $s $g[2] $g[3] 2.82 1.36 $g[4] $g[5] 1 -Round | Out-Null
        Add-Text $s $g[0] ($g[2] + 0.16) ($g[3] + 0.16) 1.2 0.23 13 $Theme.Ink -Bold 1 $TitleFont | Out-Null
        Add-Text $s $g[1] ($g[2] + 0.16) ($g[3] + 0.5) 2.35 0.72 9 $Theme.Text 1 $Font | Out-Null
        Add-Line $s ($g[2] + 1.41) ($g[3] + 0.68) 6.5 3.05 $g[5] 1.2 | Out-Null
    }
    Add-Stat $s 4.22 1.12 1.15 "35" "建表脚本覆盖" $Theme.Coral
    Add-Stat $s 5.62 1.12 1.15 "5" "核心业务域" $Theme.Mint
    Add-Stat $s 7.02 1.12 1.15 "Redis" "热点与异步数据" $Theme.Amber
    Add-Footer $s $slideNo

    # 7. Progress
    $slideNo++
    $s = $deck.Slides.Add($slideNo, 12)
    $s.FollowMasterBackground = 0
    $s.Background.Fill.ForeColor.RGB = Color $Theme.Paper
    Add-Header $s "阶段成果与开发进度" "中期阶段已具备可演示主流程，后续集中补强质量与交付"
    Add-Stat $s 0.72 1.34 2.0 "173" "后端 Java 文件" $Theme.Mint
    Add-Stat $s 3.02 1.34 2.0 "118" "接口映射" $Theme.Coral
    Add-Stat $s 5.32 1.34 2.0 "28" "前端页面" $Theme.Amber
    Add-Stat $s 7.62 1.34 2.0 "18" "测试类" $Theme.Teal
    Add-Rect $s 10.2 1.34 2.08 1.12 $Theme.Ink "" 0 -Round | Out-Null
    Add-Text $s "已进入联调与验收阶段" 10.45 1.68 1.55 0.26 13 "FFFFFF" -Bold 2 $TitleFont | Out-Null
    Add-Rect $s 0.86 3.15 11.32 2.58 $Theme.Card $Theme.Line 1 -Round | Out-Null
    Add-Progress $s 1.16 3.48 6.85 "账户认证与个人中心" 90 $Theme.Mint
    Add-Progress $s 1.16 3.88 6.85 "店铺详情、搜索、地图周边" 85 $Theme.Coral
    Add-Progress $s 1.16 4.28 6.85 "优惠券、秒杀、订单状态流转" 82 $Theme.Amber
    Add-Progress $s 1.16 4.68 6.85 "笔记、评论、点赞、关注与 feed" 80 $Theme.Teal
    Add-Progress $s 1.16 5.08 6.85 "商家入驻、审核、券管理" 78 $Theme.Mint
    Add-Progress $s 1.16 5.48 6.85 "自动化测试、压测、部署文档" 55 $Theme.Coral
    Add-Text $s "说明：百分比为按模块完成度估算，依据代码、页面与测试现状，用于中期汇报展示。" 8.8 3.55 2.9 1.16 10 $Theme.Muted 1 $Font | Out-Null
    Add-Footer $s $slideNo

    # 8. Auth
    $slideNo++
    $s = $deck.Slides.Add($slideNo, 12)
    $s.FollowMasterBackground = 0
    $s.Background.Fill.ForeColor.RGB = Color $Theme.Paper
    Add-Header $s "核心实现一：登录认证与会话续期" "用 Redis 替代服务端 Session，支撑分布式访问与单端登录控制"
    Add-Node $s "手机号/密码或验证码" 0.82 2.2 1.75 0.68 $Theme.SoftMint $Theme.Mint
    Add-Line $s 2.62 2.54 3.18 2.54 $Theme.Mint 2 -Arrow | Out-Null
    Add-Node $s "校验用户与验证码" 3.24 2.2 1.75 0.68 $Theme.Card $Theme.Line
    Add-Line $s 5.04 2.54 5.6 2.54 $Theme.Coral 2 -Arrow | Out-Null
    Add-Node $s "生成 token" 5.66 2.2 1.45 0.68 $Theme.SoftCoral $Theme.Coral
    Add-Line $s 7.16 2.54 7.72 2.54 $Theme.Coral 2 -Arrow | Out-Null
    Add-Node $s "Redis Hash 保存用户信息" 7.78 2.2 2.0 0.68 $Theme.SoftAmber $Theme.Amber
    Add-Line $s 9.83 2.54 10.39 2.54 $Theme.Mint 2 -Arrow | Out-Null
    Add-Node $s "拦截器刷新 TTL" 10.45 2.2 1.75 0.68 $Theme.SoftMint $Theme.Mint
    Add-Card $s 0.9 4.02 3.5 1.32 "Redis Key 设计" "login:code:{phone} 存验证码；login:token:{token} 存用户摘要；login:user:{id} 记录当前活跃 token。" $Theme.Mint
    Add-Card $s 4.9 4.02 3.5 1.32 "安全控制" "登录成功后清理旧 token；请求进入 RefreshTokenInterceptor 自动续期；未登录接口由 LoginInterceptor 拦截。" $Theme.Coral
    Add-Card $s 8.9 4.02 3.3 1.32 "中期价值" "认证状态从 JVM 内存迁移到 Redis，便于多实例扩展，也方便后续做在线用户、踢下线等能力。" $Theme.Amber
    Add-Footer $s $slideNo

    # 9. Shop cache and geo
    $slideNo++
    $s = $deck.Slides.Add($slideNo, 12)
    $s.FollowMasterBackground = 0
    $s.Background.Fill.ForeColor.RGB = Color $Theme.Paper
    Add-Header $s "核心实现二：店铺缓存、搜索与地图周边" "以缓存降读压，以 GEO 与高德 API 扩展附近商家能力"
    Add-Rect $s 0.82 1.42 3.42 4.65 $Theme.Card $Theme.Line 1 -Round | Out-Null
    Add-Text $s "缓存策略" 1.1 1.75 1.3 0.28 16 $Theme.Ink -Bold 1 $TitleFont | Out-Null
    Add-Text $s "缓存空值：防止穿透`n互斥锁：重建热点店铺缓存`n逻辑过期：降低击穿影响`nTTL 抖动：缓解同刻过期雪崩" 1.1 2.3 2.65 1.42 12 $Theme.Text 1 $Font | Out-Null
    Add-Rect $s 1.1 4.42 2.38 0.54 $Theme.SoftMint $Theme.Mint 1 -Round | Out-Null
    Add-Text $s "CacheClient 封装通用模板" 1.26 4.6 2.02 0.16 10 $Theme.Text -Bold 2 $Font | Out-Null
    Add-Rect $s 4.9 1.42 3.42 4.65 $Theme.Card $Theme.Line 1 -Round | Out-Null
    Add-Text $s "附近搜索" 5.18 1.75 1.3 0.28 16 $Theme.Ink -Bold 1 $TitleFont | Out-Null
    Add-Node $s "用户坐标" 5.18 2.36 1.0 0.44 $Theme.SoftCoral $Theme.Coral
    Add-Line $s 6.22 2.58 6.7 2.58 $Theme.Coral 1.6 -Arrow | Out-Null
    Add-Node $s "Redis GEO" 6.75 2.36 1.05 0.44 $Theme.SoftMint $Theme.Mint
    Add-Node $s "按类型半径检索`n返回距离排序" 5.65 3.2 1.9 0.72 $Theme.SoftAmber $Theme.Amber
    Add-Text $s "shop:geo:{typeId} 维护门店坐标；ShopNearbySupport 与 QuerySupport 负责距离、分页和兜底。" 5.18 4.36 2.58 0.68 10 $Theme.Muted 1 $Font | Out-Null
    Add-Rect $s 8.98 1.42 3.42 4.65 $Theme.Card $Theme.Line 1 -Round | Out-Null
    Add-Text $s "高德增强" 9.26 1.75 1.3 0.28 16 $Theme.Ink -Bold 1 $TitleFont | Out-Null
    Add-Node $s "地图配置" 9.25 2.3 1.05 0.42 $Theme.SoftMint $Theme.Mint
    Add-Node $s "POI 搜索" 10.65 2.3 1.05 0.42 $Theme.SoftCoral $Theme.Coral
    Add-Node $s "逆地理" 9.25 3.04 1.05 0.42 $Theme.SoftAmber $Theme.Amber
    Add-Node $s "同步入库" 10.65 3.04 1.05 0.42 "EEF0F2" $Theme.Teal
    Add-Text $s "外部 POI 与本地商家模型解耦，既能查询实时周边，也能逐步沉淀到平台数据。" 9.25 4.18 2.52 0.68 10 $Theme.Muted 1 $Font | Out-Null
    Add-Footer $s $slideNo

    # 10. Voucher seckill
    $slideNo++
    $s = $deck.Slides.Add($slideNo, 12)
    $s.FollowMasterBackground = 0
    $s.Background.Fill.ForeColor.RGB = Color $Theme.Paper
    Add-Header $s "核心实现三：优惠券秒杀与订单流转" "Lua 原子校验 + Redis Stream 异步消费，降低数据库瞬时压力"
    $seq = @(
        @("1", "用户抢券", "请求 voucher-order/seckill/{id}", $Theme.Mint),
        @("2", "Lua 原子判断", "库存 > 0 且一人一单", $Theme.Coral),
        @("3", "写入 Stream", "stream.orders 进入异步队列", $Theme.Amber),
        @("4", "后台消费者", "读取消息并处理 pending-list", $Theme.Teal),
        @("5", "事务落库", "扣库存、创建订单、ACK", $Theme.Mint)
    )
    for ($i = 0; $i -lt $seq.Count; $i++) {
        $x = 0.72 + $i * 2.47
        Add-Circle $s ($x + 0.56) 1.74 0.54 $seq[$i][3] | Out-Null
        Add-Text $s $seq[$i][0] ($x + 0.72) 1.9 0.22 0.18 10 "FFFFFF" -Bold 2 $TitleFont | Out-Null
        Add-Rect $s $x 2.45 1.68 1.25 $Theme.Card $Theme.Line 1 -Round | Out-Null
        Add-Text $s $seq[$i][1] ($x + 0.16) 2.7 1.36 0.24 12 $Theme.Ink -Bold 2 $TitleFont | Out-Null
        Add-Text $s $seq[$i][2] ($x + 0.16) 3.07 1.36 0.34 8.8 $Theme.Muted 2 $Font | Out-Null
        if ($i -lt $seq.Count - 1) { Add-Line $s ($x + 1.78) 3.08 ($x + 2.35) 3.08 $seq[$i][3] 1.8 -Arrow | Out-Null }
    }
    Add-Card $s 0.9 4.82 3.45 1.1 "关键约束" "Redis Set 记录已下单用户，Lua 脚本一次性完成库存判断、重复下单判断、扣减和消息投递。" $Theme.Coral
    Add-Card $s 4.82 4.82 3.45 1.1 "订单状态" "支持待支付、已支付、已使用、已取消、退款等状态，并提供用户端与商家端查询。" $Theme.Amber
    Add-Card $s 8.75 4.82 3.35 1.1 "失败兜底" "消费者异常时处理 pending-list；数据库事务保证库存与订单数据一致。" $Theme.Mint
    Add-Footer $s $slideNo

    # 11. Social
    $slideNo++
    $s = $deck.Slides.Add($slideNo, 12)
    $s.FollowMasterBackground = 0
    $s.Background.Fill.ForeColor.RGB = Color $Theme.Paper
    Add-Header $s "核心实现四：内容社区与用户关系" "用 ZSet、Feed 和隐私设置提升用户粘性"
    Add-Rect $s 0.8 1.38 5.5 4.92 $Theme.Card $Theme.Line 1 -Round | Out-Null
    Add-Text $s "内容互动链路" 1.08 1.72 1.55 0.28 16 $Theme.Ink -Bold 1 $TitleFont | Out-Null
    Add-Node $s "发布探店笔记" 1.08 2.4 1.42 0.5 $Theme.SoftMint $Theme.Mint
    Add-Line $s 2.56 2.65 3.02 2.65 $Theme.Mint 1.6 -Arrow | Out-Null
    Add-Node $s "粉丝 Feed 推送" 3.08 2.4 1.42 0.5 $Theme.SoftCoral $Theme.Coral
    Add-Line $s 4.56 2.65 5.02 2.65 $Theme.Coral 1.6 -Arrow | Out-Null
    Add-Node $s "滚动分页浏览" 5.08 2.4 0.82 0.5 $Theme.SoftAmber $Theme.Amber
    Add-Text $s "点赞使用 blog:liked:{id} 的 ZSet 保存用户与时间分数，既能判断是否点赞，也能按时间取前排点赞用户。" 1.08 3.5 4.5 0.62 11 $Theme.Text 1 $Font | Out-Null
    Add-Text $s "评论支持楼中楼回复、评论点赞与删除校验，形成完整社区互动闭环。" 1.08 4.42 4.5 0.42 11 $Theme.Muted 1 $Font | Out-Null
    Add-Rect $s 6.98 1.38 5.35 4.92 $Theme.Ink "" 0 -Round | Out-Null
    Add-Text $s "用户沉淀能力" 7.34 1.72 1.8 0.28 16 "FFFFFF" -Bold 1 $TitleFont | Out-Null
    Add-Card $s 7.35 2.32 1.95 1.1 "关注" "互关、共同关注、粉丝/关注列表" $Theme.Mint
    Add-Card $s 9.75 2.32 1.95 1.1 "收藏" "店铺与笔记收藏，防重复约束" $Theme.Coral
    Add-Card $s 7.35 3.78 1.95 1.1 "足迹" "Redis ZSet 保留近 100 条浏览历史" $Theme.Amber
    Add-Card $s 9.75 3.78 1.95 1.1 "隐私" "控制关注、粉丝、收藏列表可见性" $Theme.Mint
    Add-Text $s "消息模块向收藏店铺用户推送门店更新，并缓存未读数，支撑后续运营触达。" 7.38 5.45 4.1 0.36 10.5 "DDF3EA" 1 $Font | Out-Null
    Add-Footer $s $slideNo

    # 12. Merchant admin
    $slideNo++
    $s = $deck.Slides.Add($slideNo, 12)
    $s.FollowMasterBackground = 0
    $s.Background.Fill.ForeColor.RGB = Color $Theme.Paper
    Add-Header $s "商家入驻与运营审核" "补齐平台业务闭环：从用户内容平台扩展到商家经营后台"
    Add-Rect $s 0.9 1.52 11.48 1.02 $Theme.Ink "" 0 -Round | Out-Null
    Add-Text $s "入驻申请" 1.2 1.86 1.0 0.22 12 "FFFFFF" -Bold 2 $Font | Out-Null
    Add-Line $s 2.35 2.02 3.05 2.02 "DDF3EA" 1.6 -Arrow | Out-Null
    Add-Text $s "管理员审核" 3.1 1.86 1.1 0.22 12 "FFFFFF" -Bold 2 $Font | Out-Null
    Add-Line $s 4.4 2.02 5.1 2.02 "DDF3EA" 1.6 -Arrow | Out-Null
    Add-Text $s "绑定门店/创建门店" 5.15 1.86 1.65 0.22 12 "FFFFFF" -Bold 2 $Font | Out-Null
    Add-Line $s 6.95 2.02 7.65 2.02 "DDF3EA" 1.6 -Arrow | Out-Null
    Add-Text $s "发布优惠券" 7.72 1.86 1.18 0.22 12 "FFFFFF" -Bold 2 $Font | Out-Null
    Add-Line $s 9.05 2.02 9.75 2.02 "DDF3EA" 1.6 -Arrow | Out-Null
    Add-Text $s "核销订单" 9.82 1.86 1.05 0.22 12 "FFFFFF" -Bold 2 $Font | Out-Null
    Add-Card $s 0.95 3.24 2.6 1.55 "商家申请" "tb_merchant_application 保存入驻资料与审核状态；通过后写入角色与商家身份。" $Theme.Mint
    Add-Card $s 3.88 3.24 2.6 1.55 "门店认领/新建" "支持对已有门店发起认领，也支持商家提交新店创建申请，管理员统一处理。" $Theme.Coral
    Add-Card $s 6.82 3.24 2.6 1.55 "优惠券经营" "商家维护券信息，发布时同步秒杀库存到 Redis；下架时清理缓存库存。" $Theme.Amber
    Add-Card $s 9.75 3.24 2.35 1.55 "订单核销" "商家端按门店查看券订单，通过核销码完成线下消费闭环。" $Theme.Teal
    Add-Text $s "中期价值：把用户侧点评系统升级为具备商家供给、平台审核和交易履约能力的综合本地生活系统。" 1.22 5.72 10.2 0.32 13 $Theme.Ink -Bold 2 $Font | Out-Null
    Add-Footer $s $slideNo

    # 13. AI and Map
    $slideNo++
    $s = $deck.Slides.Add($slideNo, 12)
    $s.FollowMasterBackground = 0
    $s.Background.Fill.ForeColor.RGB = Color $Theme.Paper
    Add-Header $s "AI 助手与智能搜索增强" "DeepSeek 负责自然语言理解，高德地图负责位置语义，后端做动作路由"
    Add-Rect $s 0.88 1.42 3.4 4.68 $Theme.Ink "" 0 -Round | Out-Null
    Add-Text $s "用户自然语言" 1.22 1.86 1.45 0.24 14 "FFFFFF" -Bold 1 $TitleFont | Out-Null
    Add-Text $s "“帮我找附近评分高的咖啡店，看看有没有优惠券。”" 1.22 2.42 2.52 0.72 18 "DDF3EA" -Bold 1 $Font | Out-Null
    Add-Text $s "聊天接口同时返回 reply 与 actions，前端可直接渲染跳转按钮。" 1.22 4.58 2.56 0.52 11 "BBD0CA" 1 $Font | Out-Null
    Add-Line $s 4.45 3.22 5.18 3.22 $Theme.Coral 2 -Arrow | Out-Null
    Add-Node $s "AssistantRoutingSupport`n关键词清洗 / 意图识别 / 动作组合" 5.25 2.66 2.6 1.1 $Theme.SoftCoral $Theme.Coral
    Add-Line $s 7.95 3.22 8.65 2.3 $Theme.Mint 1.6 -Arrow | Out-Null
    Add-Line $s 7.95 3.22 8.65 4.08 $Theme.Amber 1.6 -Arrow | Out-Null
    Add-Card $s 8.75 1.68 2.9 1.2 "DeepSeekChat" "生成自然语言回复，增强对话体验；失败时使用本地路由兜底。" $Theme.Mint
    Add-Card $s 8.75 3.62 2.9 1.2 "搜索与地图动作" "推荐跳转搜索页、优惠券页、订单页或写笔记页，并带上关键词参数。" $Theme.Amber
    Add-Rect $s 5.2 5.2 6.48 0.58 $Theme.Card $Theme.Line 1 -Round | Out-Null
    Add-Text $s "相关接口：/assistant/chat、/assistant/chat/stream、/search、/map/nearby、/map/search" 5.46 5.42 5.85 0.16 9.6 $Theme.Muted 2 $Font | Out-Null
    Add-Footer $s $slideNo

    # 14. Testing
    $slideNo++
    $s = $deck.Slides.Add($slideNo, 12)
    $s.FollowMasterBackground = 0
    $s.Background.Fill.ForeColor.RGB = Color $Theme.Paper
    Add-Header $s "测试与质量保障" "围绕高风险模块做单测，后续补充端到端与压测"
    Add-Stat $s 0.78 1.38 1.72 "18" "测试类" $Theme.Mint
    Add-Stat $s 2.82 1.38 1.72 "1,403" "测试代码行" $Theme.Coral
    Add-Stat $s 4.86 1.38 1.72 "3" "Lua 脚本" $Theme.Amber
    Add-Stat $s 6.9 1.38 1.72 "11k+" "后端代码行" $Theme.Teal
    Add-Rect $s 9.28 1.38 2.72 1.12 $Theme.Ink "" 0 -Round | Out-Null
    Add-Text $s "质量目标：先保核心链路，再扩展覆盖率" 9.58 1.74 2.05 0.28 12.5 "FFFFFF" -Bold 2 $TitleFont | Out-Null
    Add-Card $s 0.9 3.12 2.9 1.58 "已覆盖重点" "用户登录与 Token 刷新、秒杀订单、优惠券视图、地图搜索、AI 助手路由、搜索关键词、附近查询。" $Theme.Mint
    Add-Card $s 4.18 3.12 2.9 1.58 "质量机制" "全局异常处理、参数校验、事务回滚、Redis 脚本原子性、pending-list 兜底、AOP 限流。" $Theme.Coral
    Add-Card $s 7.46 3.12 2.9 1.58 "下一步测试" "补充接口集成测试、前端冒烟测试、秒杀并发压测、部署环境配置检查。" $Theme.Amber
    Add-Rect $s 10.92 3.12 1.08 1.58 $Theme.SoftMint $Theme.Mint 1 -Round | Out-Null
    Add-Text $s "中期结论`n核心链路已具备验收基础" 11.1 3.45 0.72 0.72 11 $Theme.Text -Bold 2 $Font | Out-Null
    Add-Footer $s $slideNo

    # 15. Risks and plan
    $slideNo++
    $s = $deck.Slides.Add($slideNo, 12)
    $s.FollowMasterBackground = 0
    $s.Background.Fill.ForeColor.RGB = Color $Theme.Paper
    Add-Header $s "当前问题、风险与后续计划" "后半程目标：稳定、可部署、可演示、可答辩"
    Add-Card $s 0.78 1.4 3.45 1.25 "问题一：稳定性验证不足" "秒杀、搜索、地图和 AI 外部调用需要更多异常场景与并发压测。" $Theme.Coral
    Add-Card $s 4.9 1.4 3.45 1.25 "问题二：部署闭环未完成" "数据库初始化、Redis Stream 消费组、前端静态资源代理仍需形成一键化说明。" $Theme.Amber
    Add-Card $s 9.02 1.4 3.2 1.25 "问题三：安全配置需收口" "第三方 Key、数据库口令、跨域与上传限制需要完成脱敏与环境隔离。" $Theme.Mint
    $plan = @(
        @("第 1 周", "整理演示数据与数据库脚本，补齐部署文档", $Theme.Mint),
        @("第 2 周", "补充接口集成测试、前端冒烟检查与异常兜底", $Theme.Coral),
        @("第 3 周", "完成秒杀/缓存压测，优化热点与失败恢复", $Theme.Amber),
        @("第 4 周", "制作最终答辩材料、演示录屏与项目总结", $Theme.Teal)
    )
    for ($i = 0; $i -lt $plan.Count; $i++) {
        $x = 1.12 + $i * 2.85
        Add-Circle $s ($x + 0.8) 4.0 0.42 $plan[$i][2] | Out-Null
        Add-Line $s ($x + 1.22) 4.21 ($x + 2.5) 4.21 $(if ($i -lt 3) { $Theme.Line } else { $Theme.Paper }) 1.5 | Out-Null
        Add-Text $s $plan[$i][0] $x 4.62 2.05 0.24 12 $Theme.Ink -Bold 2 $TitleFont | Out-Null
        Add-Text $s $plan[$i][1] ($x - 0.16) 5.02 2.35 0.52 10 $Theme.Muted 2 $Font | Out-Null
    }
    Add-Rect $s 1.0 6.26 11.18 0.44 $Theme.Ink "" 0 -Round | Out-Null
    Add-Text $s "最终交付目标：一个可部署运行的本地生活点评平台，一套完整数据库脚本，一份测试报告，一段稳定演示流程。" 1.2 6.42 10.75 0.15 10.5 "FFFFFF" -Bold 2 $Font | Out-Null
    Add-Footer $s $slideNo

    # 16. Closing
    $slideNo++
    $s = $deck.Slides.Add($slideNo, 12)
    $s.FollowMasterBackground = 0
    $s.Background.Fill.ForeColor.RGB = Color $Theme.Ink
    Add-Circle $s -0.9 4.8 3.2 $Theme.Mint | Out-Null
    Add-Circle $s 10.6 -0.9 3.4 $Theme.Coral | Out-Null
    Add-Text $s "阶段总结" 0.92 1.32 3.2 0.6 32 "FFFFFF" -Bold 1 $TitleFont | Out-Null
    Add-Text $s "本项目已完成从基础点评系统到“本地生活服务平台”的核心扩展：用户、商家、平台三端主流程基本贯通，Redis 高并发能力与 AI/地图增强功能已落地。" 0.96 2.24 6.55 1.0 17 "DDF3EA" -Bold 1 $Font | Out-Null
    Add-Rect $s 0.96 4.14 5.9 1.05 "21302D" "3B4E49" 1 -Round | Out-Null
    Add-Text $s "下一阶段将重点完成：部署交付、压测优化、配置安全、演示脚本与最终论文/报告素材沉淀。" 1.24 4.46 5.32 0.38 14 "FFFFFF" -Bold 2 $Font | Out-Null
    Add-Text $s "感谢各位老师批评指正" 7.7 5.78 4.2 0.42 24 "FFFFFF" -Bold 2 $TitleFont | Out-Null

    $outPath = Join-Path $OutputDir "周边优选通-中期答辩.pptx"
    $deck.SaveAs($outPath)
    $deck.Export($PreviewDir, "PNG", 1600, 900)
    Write-Output $outPath
    Write-Output $PreviewDir
}
finally {
    if ($deck -ne $null) { $deck.Close() | Out-Null }
    if ($ppt -ne $null) { $ppt.Quit() | Out-Null }
    [GC]::Collect()
    [GC]::WaitForPendingFinalizers()
}




