package com.hmdp.service.impl;

import cn.hutool.core.util.StrUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.hmdp.dto.CityOverviewDTO;
import com.hmdp.dto.CityProfileDTO;
import com.hmdp.dto.CityScenePackDTO;
import com.hmdp.dto.MerchantCityStatsDTO;
import com.hmdp.entity.Shop;
import com.hmdp.entity.Voucher;
import com.hmdp.entity.VoucherOrder;
import com.hmdp.mapper.ShopMapper;
import com.hmdp.mapper.VoucherMapper;
import com.hmdp.mapper.VoucherOrderMapper;
import com.hmdp.service.ICityService;
import org.springframework.stereotype.Service;

import javax.annotation.PostConstruct;
import javax.annotation.Resource;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

@Service
public class CityServiceImpl implements ICityService {

    private static final String DEFAULT_CITY_CODE = "430100";

    private final Map<String, CityProfileDTO> profileMap = new LinkedHashMap<>();
    private final Map<String, List<CityScenePackDTO>> sceneMap = new LinkedHashMap<>();
    private final Set<String> hotCityCodes = new LinkedHashSet<>(Arrays.asList(
            "110100", "310100", "440100", "440300", "330100", "510100", "610100", "430100"
    ));

    @Resource
    private ShopMapper shopMapper;

    @Resource
    private VoucherMapper voucherMapper;

    @Resource
    private VoucherOrderMapper voucherOrderMapper;

    @PostConstruct
    public void init() {
        registerCity(
                cityProfile("110100", "北京", "北京", 116.4074, 39.9042,
                        "老城新潮，一天逛出北京层次感",
                        "从胡同老字号到夜场新地标，给你一套更像北京人的消费路径",
                        list("老字号", "胡同", "展览", "夜场"),
                        list("京味早餐", "下班聚餐", "博物馆周边", "夜宵续摊"),
                        list("美食", "酒吧", "亲子游乐", "健身运动"),
                        "品质感偏稳，适合做经典场景决策",
                        theme("#d55b3d", "#7a3022", "#fff7f2", "#ffe2d7"),
                        list("三里屯", "国贸", "什刹海", "簋街"),
                        list("春日赏花", "暑期亲子", "秋日看展", "冬夜涮锅"),
                        list("北京烤鸭", "胡同咖啡", "夜游什刹海", "国贸聚餐"),
                        list("老北京一日吃喝", "看展到晚餐的东城路线", "夜场续摊不绕路"),
                        true),
                scenes("110100",
                        scene("beijing-lunch", "工作日午餐", "想吃稳、快、体面一点", "国贸午餐", 1L, "写字楼周边", "地铁一站解决午饭", "我在北京工作日中午想找体面又效率高的吃饭选择", list("效率", "商务", "快决策")),
                        scene("beijing-night", "夜场续摊", "下班后还能继续坐一会", "三里屯夜场", 8L, "三里屯", "餐酒到夜场连起来", "我今晚在北京想先吃饭再续摊，别太折腾", list("社交", "夜生活", "不冷场")),
                        scene("beijing-family", "亲子半日", "博物馆周边更适合顺路安排", "北京亲子半日", 7L, "东城/朝阳", "看展+吃饭一起安排", "在北京带娃半天怎么安排更顺", list("亲子", "顺路", "省心")),
                        scene("beijing-hutong", "胡同 citywalk", "边逛边吃，不用来回折返", "胡同citywalk", 1L, "什刹海", "胡同小路串联夜宵点", "在北京想走胡同路线，边逛边吃怎么安排", list("城市肌理", "老城", "散步感")))
        );
        registerCity(
                cityProfile("310100", "上海", "上海", 121.4737, 31.2304,
                        "高效、体面、好看，上海消费要有节奏感",
                        "先替你把效率和质感排好，再决定今天去哪一家",
                        list("精致", "效率", "西餐", "展览"),
                        list("午休快吃", "约会晚餐", "展后续摊", "周末慢逛"),
                        list("美食", "美容SPA", "酒吧", "丽人·美发"),
                        "高客单更重视环境和效率",
                        theme("#ff7a45", "#24304f", "#fff8f3", "#ffe8d8"),
                        list("静安寺", "新天地", "徐家汇", "陆家嘴"),
                        list("梧桐区散步", "梅雨季室内局", "夜景约会", "周末 brunch"),
                        list("上海brunch", "静安咖啡", "陆家嘴夜景餐厅", "新天地约会"),
                        list("梧桐区慢逛路线", "展览到晚餐的静安版", "效率型商务聚餐"),
                        true),
                scenes("310100",
                        scene("shanghai-brunch", "周末 brunch", "拍照、聊天、慢一点都要兼顾", "上海brunch", 1L, "静安/徐汇", "早餐到下午茶不换区", "周末在上海想找好看又不拥挤的 brunch", list("周末", "拍照", "松弛")),
                        scene("shanghai-date", "约会晚餐", "环境、灯光、上菜节奏都要在线", "上海约会餐厅", 1L, "新天地/陆家嘴", "晚餐后顺路夜景", "今晚在上海约会，想找有质感的晚餐", list("约会", "氛围", "质感")),
                        scene("shanghai-office", "午休快吃", "别排队太久，也别吃得太敷衍", "写字楼午餐", 1L, "陆家嘴/静安", "写字楼到餐厅5分钟", "我在上海工作日中午想吃点体面又快的", list("效率", "通勤", "工作日")),
                        scene("shanghai-night", "展后续摊", "看完展想继续坐坐，不想换两次车", "上海展后续摊", 8L, "静安/黄浦", "展览和夜场就近串联", "在上海看完展之后还能去哪儿坐一会", list("看展", "社交", "夜生活")))
        );
        registerCity(
                cityProfile("440100", "广州", "广东", 113.2644, 23.1291,
                        "白天讲效率，晚上讲烟火，广州吃喝要踩准时间点",
                        "把宵夜、老广口味和商圈节奏一起算进去，推荐才靠谱",
                        list("烟火气", "老字号", "夜宵", "粤味"),
                        list("早茶", "夜宵", "朋友聚会", "周末扫街"),
                        list("美食", "酒吧", "亲子游乐", "按摩·足疗"),
                        "价格带更宽，适合高频复购型消费",
                        theme("#ff7043", "#7a2315", "#fff7f3", "#ffe0d6"),
                        list("天河路", "北京路", "琶洲", "上下九"),
                        list("夏夜大排档", "老广早茶", "节日夜游", "台风天室内局"),
                        list("广州夜宵", "老广早茶", "广州烧鹅", "北京路边吃边逛"),
                        list("老广早茶一天", "夜宵从烧烤到糖水", "北京路扫街路线"),
                        true),
                scenes("440100",
                        scene("guangzhou-morning", "老广早茶", "一家人慢慢吃，也适合商务见面", "广州早茶", 1L, "越秀/荔湾", "早茶后顺路逛老城", "在广州想找一顿像样的早茶应该怎么选", list("家庭", "传统", "周末")),
                        scene("guangzhou-supper", "深夜宵夜", "够味、够晚、别太远", "广州夜宵", 1L, "天河/海珠", "夜宵后不折返", "今晚在广州想吃夜宵，哪里更靠谱", list("夜生活", "烟火", "朋友局")),
                        scene("guangzhou-friends", "朋友聚会", "热闹一点、口味稳一点", "广州朋友聚会", 1L, "天河路", "吃完还能喝一轮", "在广州和朋友聚会怎么安排更热闹", list("社交", "聚会", "热闹")),
                        scene("guangzhou-walk", "周末扫街", "一路能吃能逛，越走越有意思", "广州周末扫街", 1L, "北京路/上下九", "老城街区连着吃", "周末在广州想边走边吃，怎么安排路线", list("citywalk", "老城", "小吃")))
        );
        registerCity(
                cityProfile("440300", "深圳", "广东", 114.0579, 22.5431,
                        "快、准、爽，深圳下班后的选择不能拖泥带水",
                        "更适合高效率消费决策，下班就能直接走到场景里",
                        list("效率", "新消费", "夜场", "商务"),
                        list("下班聚餐", "轻运动", "夜咖啡", "周末海边"),
                        list("美食", "健身运动", "酒吧", "美容SPA"),
                        "节奏快，适合通勤友好型推荐",
                        theme("#ff6a3d", "#102542", "#fff8f3", "#ffe1d4"),
                        list("南山", "福田", "万象天地", "海岸城"),
                        list("雨天商场局", "海边日落", "深夜加班餐", "周末轻运动"),
                        list("深圳下班聚餐", "南山夜咖啡", "深圳海边晚餐", "福田效率午餐"),
                        list("深南大道下班路线", "海边日落到晚餐", "万象天地社交局"),
                        true),
                scenes("440300",
                        scene("shenzhen-office", "下班聚餐", "工作日也要快点决定，别让大家等", "深圳下班聚餐", 1L, "南山/福田", "地铁口直达", "在深圳下班后和同事吃饭，哪里效率高一点", list("工作日", "效率", "同事局")),
                        scene("shenzhen-fitness", "轻运动后吃点好的", "先动一动，再找靠谱的恢复餐", "深圳轻运动", 10L, "南山/福田", "运动和餐饮顺路", "在深圳运动完适合去哪儿吃", list("运动", "恢复", "健康")),
                        scene("shenzhen-night", "夜咖啡", "不想太吵，但想把状态拉回来", "深圳夜咖啡", 1L, "南山", "咖啡后还能继续聊", "今晚在深圳想找个夜咖啡坐坐", list("夜晚", "聊天", "轻社交")),
                        scene("shenzhen-weekend", "周末海边", "风景和氛围都在线", "深圳海边晚餐", 1L, "深圳湾", "日落到晚餐一条线", "周末在深圳想去海边，怎么安排吃喝更顺", list("海边", "周末", "氛围")))
        );
        registerCity(
                cityProfile("330100", "杭州", "浙江", 120.1551, 30.2741,
                        "西湖不只是景点，杭州要把江南感和周末感一起安排",
                        "更适合慢半拍的城市版本，留白、景致和体验感都重要",
                        list("江南", "咖啡", "周末感", "citywalk"),
                        list("西湖漫游", "朋友下午茶", "周末轻聚餐", "夜景散步"),
                        list("美食", "丽人·美发", "美容SPA", "亲子游乐"),
                        "消费更偏周末体验和品质休闲",
                        theme("#eb6b2d", "#1b4d3e", "#fff9f2", "#d8efe3"),
                        list("西湖", "湖滨", "天目里", "钱江新城"),
                        list("梅雨季室内馆", "秋日桂花", "周末西湖", "夜游钱江"),
                        list("西湖下午茶", "杭州小酒馆", "杭州周末约会", "天目里看展"),
                        list("西湖半日慢游", "天目里到晚餐", "湖滨夜游路线"),
                        true),
                scenes("330100",
                        scene("hangzhou-westlake", "周末 citywalk", "不急，想把风景和吃喝都照顾到", "西湖citywalk", 1L, "西湖/湖滨", "湖边到晚餐不折返", "在杭州周末想走一条松弛一点的路线", list("周末", "citywalk", "景色")),
                        scene("hangzhou-tea", "朋友下午茶", "既能坐久一点，也要好看一点", "杭州下午茶", 1L, "西湖/天目里", "下午茶后顺手逛展", "在杭州和朋友喝下午茶去哪更有感觉", list("下午茶", "朋友局", "好看")),
                        scene("hangzhou-date", "江南约会", "不喧闹，适合慢慢聊", "杭州约会晚餐", 1L, "湖滨/钱江新城", "约会到夜景直接串起来", "今晚在杭州约会应该怎么安排更有江南感", list("约会", "夜景", "氛围")),
                        scene("hangzhou-family", "亲子半日", "轻松、顺路、不折腾小朋友", "杭州亲子半日", 7L, "滨江/西湖", "一条线完成吃喝玩", "在杭州带娃半天怎么安排比较省心", list("亲子", "省心", "周末")))
        );
        registerCity(
                cityProfile("320100", "南京", "江苏", 118.7969, 32.0603,
                        "六朝气质和年轻生活并存，南京适合做层次感消费",
                        "白天有文化底色，晚上有夜游和夜宵，路线感很重要",
                        list("古都", "夜游", "鸭血粉丝", "梧桐路"),
                        list("秦淮夜游", "工作日午餐", "朋友小聚", "周末散步"),
                        list("美食", "酒吧", "亲子游乐", "健身运动"),
                        "适合文化体验和夜间消费联动",
                        theme("#cc5a36", "#52281d", "#fff8f3", "#ffe3d7"),
                        list("新街口", "夫子庙", "老门东", "河西"),
                        list("梅花季", "秦淮灯会", "秋天梧桐路", "周末博物馆"),
                        list("南京夜游", "老门东小吃", "新街口聚餐", "南京下午茶"),
                        list("秦淮河夜游路线", "老门东吃逛一条线", "新街口效率局"),
                        true),
                scenes("320100",
                        scene("nanjing-night", "秦淮夜游", "先逛再吃，别让路线断掉", "南京夜游", 1L, "夫子庙/老门东", "夜游和晚餐直接连着走", "在南京晚上想边逛边吃怎么安排", list("夜游", "文化", "散步")),
                        scene("nanjing-office", "工作日午餐", "快、稳、离办公区近", "新街口午餐", 1L, "新街口", "通勤友好", "在南京工作日中午想找效率高的饭局", list("效率", "通勤", "工作日")),
                        scene("nanjing-friends", "朋友小聚", "适合聊天，也要有南京味道", "南京朋友聚会", 1L, "老门东", "吃完还能继续逛", "在南京和朋友小聚，哪里更有本地感觉", list("聚会", "本地味", "松弛")),
                        scene("nanjing-weekend", "周末散步", "文化景点和吃喝都不落下", "南京周末散步", 1L, "玄武湖/老城", "景点和餐厅一条线", "周末在南京想散步+吃饭，怎么安排", list("周末", "文化", "citywalk")))
        );
        registerCity(
                cityProfile("510100", "成都", "四川", 104.0668, 30.5728,
                        "松弛不是慢，是把吃喝和社交安排得刚刚好",
                        "成都版本更重氛围感、朋友局和夜生活，不必急着做决定",
                        list("松弛感", "火锅", "社交", "夜生活"),
                        list("下班火锅", "夜宵", "酒馆聊天", "周末摆龙门阵"),
                        list("美食", "酒吧", "轰趴馆", "按摩·足疗"),
                        "夜经济和社交型消费更活跃",
                        theme("#ff6f3c", "#5b2c1e", "#fff7f1", "#ffe4d7"),
                        list("太古里", "建设路", "玉林", "春熙路"),
                        list("雨天火锅", "夜间酒馆", "周末公园局", "深夜小吃"),
                        list("成都火锅", "玉林小酒馆", "建设路夜宵", "成都周末松弛路线"),
                        list("太古里到玉林", "火锅后去酒馆", "夜宵不绕路路线"),
                        true),
                scenes("510100",
                        scene("chengdu-hotpot", "朋友火锅局", "热闹、好约、越晚越有氛围", "成都火锅", 1L, "太古里/春熙路", "吃完还能转场", "今晚在成都和朋友吃火锅，哪里更有氛围", list("社交", "热闹", "晚饭")),
                        scene("chengdu-bar", "酒馆聊天", "不想太吵，想坐得住", "成都小酒馆", 8L, "玉林", "酒馆串联夜宵", "在成都晚上想找一个能聊天的小酒馆", list("聊天", "夜生活", "松弛")),
                        scene("chengdu-supper", "深夜宵夜", "凌晨也能吃得舒服", "成都夜宵", 1L, "建设路", "夜宵点位密度高", "半夜在成都想吃点东西去哪里", list("夜宵", "深夜", "烟火")),
                        scene("chengdu-weekend", "周末松弛路线", "轻松逛、慢慢吃，不赶行程", "成都周末路线", 1L, "玉林/望平街", "边走边吃边聊天", "周末在成都怎么安排更有松弛感", list("周末", "citywalk", "松弛")))
        );
        registerCity(
                cityProfile("420100", "武汉", "湖北", 114.3054, 30.5931,
                        "江城的魅力在桥、江、夜色，也在一顿热乎乎的晚饭",
                        "适合做桥头到商圈的路线推荐，夜景和夜宵要一起安排",
                        list("江城", "热干面", "江滩", "夜景"),
                        list("江滩散步", "朋友聚会", "宵夜", "周末打卡"),
                        list("美食", "酒吧", "亲子游乐", "轰趴馆"),
                        "夜景路线和重口味餐饮更容易成交",
                        theme("#d95d30", "#1b3558", "#fff8f3", "#ffe6d8"),
                        list("江汉路", "楚河汉街", "光谷", "汉口江滩"),
                        list("樱花季", "江滩夜风", "暑期夜游", "周末看江"),
                        list("武汉夜宵", "江汉路小吃", "武汉朋友聚会", "江滩散步"),
                        list("江汉路到江滩", "看江到夜宵", "光谷聚会局"),
                        true),
                scenes("420100",
                        scene("wuhan-river", "江滩散步", "想吹风也想顺手找一家靠谱的店", "武汉江滩散步", 1L, "江汉路/汉口江滩", "散步后直达夜宵", "在武汉晚上想去江边走走，然后吃点东西", list("夜景", "散步", "松弛")),
                        scene("wuhan-friends", "朋友聚会", "热闹一点，不想踩雷", "武汉朋友聚会", 1L, "楚河汉街/光谷", "饭局后还有下一场", "和朋友在武汉聚会应该去哪更热闹", list("聚会", "热闹", "社交")),
                        scene("wuhan-supper", "深夜热食", "夜里也得吃得踏实", "武汉夜宵", 1L, "江汉路/光谷", "夜宵点更集中", "晚上在武汉想吃点热乎的夜宵", list("夜宵", "热食", "烟火")),
                        scene("wuhan-weekend", "周末打卡", "景点和吃喝要有主线", "武汉周末路线", 1L, "武昌/汉口", "景点顺路接餐厅", "周末来武汉怎么安排一条轻松路线", list("周末", "路线", "打卡")))
        );
        registerCity(
                cityProfile("610100", "西安", "陕西", 108.9398, 34.3416,
                        "古城不只看城墙，西安要把夜游和碳水局一起安排",
                        "更适合文化夜游型推荐，先逛后吃，节奏比单点更重要",
                        list("古都", "城墙", "回民街", "夜游"),
                        list("城墙夜游", "朋友聚餐", "深夜碳水", "周末文化线"),
                        list("美食", "酒吧", "亲子游乐", "轰趴馆"),
                        "文化型目的地更适合路线化成交",
                        theme("#cf5733", "#5c2a1c", "#fff8f3", "#ffe5d8"),
                        list("大唐不夜城", "钟楼", "小寨", "回民街"),
                        list("夏夜不夜城", "节日灯会", "周末城墙", "深夜小吃"),
                        list("西安夜游", "回民街小吃", "大唐不夜城", "西安朋友聚餐"),
                        list("钟楼到回民街", "大唐不夜城夜游", "先逛后吃的西安线"),
                        true),
                scenes("610100",
                        scene("xian-night", "大唐夜游", "景、光、人气都在线，还得顺手能吃", "西安夜游", 1L, "大唐不夜城", "看完夜景就到饭点", "在西安晚上想去夜游，之后吃点什么更顺", list("夜游", "文化", "氛围")),
                        scene("xian-carb", "深夜碳水", "够香、够晚、别排太久", "西安夜宵", 1L, "钟楼/小寨", "碳水局不绕路", "半夜在西安想吃碳水，哪里更稳", list("夜宵", "碳水", "烟火")),
                        scene("xian-friends", "朋友聚餐", "要热闹，也要有城市感", "西安朋友聚餐", 1L, "小寨", "饭后还能逛一圈", "在西安和朋友吃饭去哪里更有氛围", list("聚会", "热闹", "城市感")),
                        scene("xian-weekend", "周末文化线", "景点、吃饭、散步都得连起来", "西安周末路线", 1L, "城墙/钟楼", "文化和吃喝一条线", "周末在西安怎么安排一条不累的文化路线", list("周末", "文化", "路线")))
        );
        registerCity(
                cityProfile("500100", "重庆", "重庆", 106.5516, 29.5630,
                        "山城消费讲路线，不讲平面图",
                        "先替你把爬坡、转场、夜景和火锅安排好，再决定去哪家",
                        list("山城", "江景", "火锅", "夜经济"),
                        list("夜景晚餐", "朋友火锅局", "周末爬坡 citywalk", "深夜小面"),
                        list("美食", "酒吧", "轰趴馆", "按摩·足疗"),
                        "更适合路线型推荐和夜经济成交",
                        theme("#de5b38", "#31161a", "#fff7f2", "#ffe3d6"),
                        list("解放碑", "观音桥", "洪崖洞", "南滨路"),
                        list("夜景季", "节日灯光", "周末江边", "雨夜火锅"),
                        list("重庆火锅", "洪崖洞夜景", "南滨路晚餐", "重庆夜宵"),
                        list("洪崖洞到南滨路", "山城夜景晚餐线", "火锅后夜场顺路线"),
                        true),
                scenes("500100",
                        scene("cq-night", "夜景晚餐", "看景和吃饭必须是一条路上的事", "重庆夜景晚餐", 1L, "洪崖洞/南滨路", "夜景和晚餐无缝切换", "在重庆想看夜景也想吃一顿好的，怎么安排更顺", list("夜景", "约会", "路线")),
                        scene("cq-hotpot", "朋友火锅局", "热闹、够味、还能接下一场", "重庆火锅", 1L, "观音桥/解放碑", "吃完还能逛夜场", "今晚在重庆和朋友吃火锅，哪里更适合多人局", list("火锅", "朋友局", "夜生活")),
                        scene("cq-citywalk", "山城 citywalk", "少绕路、少爬冤枉坡", "重庆citywalk", 1L, "解放碑/十八梯", "坡道路线更顺", "周末在重庆想走一条不那么累的 citywalk 路线", list("citywalk", "路线", "山城")),
                        scene("cq-supper", "深夜小面", "越晚越想来点热的", "重庆夜宵", 1L, "解放碑", "夜宵点密集", "半夜在重庆想吃点热乎的去哪里", list("夜宵", "深夜", "烟火")))
        );
        registerCity(
                cityProfile("430100", "长沙", "湖南", 112.9388, 28.2282,
                        "长沙的答案通常在夜里，越晚越有主场感",
                        "夜生活、年轻消费、重社交，是长沙版本最强的成交引擎",
                        list("夜生活", "年轻人", "小龙虾", "重社交"),
                        list("夜宵", "朋友聚会", "酒吧续摊", "周末打卡"),
                        list("美食", "酒吧", "轰趴馆", "美睫·美甲"),
                        "适合年轻人高频社交消费",
                        theme("#ff6b35", "#431b21", "#fff8f2", "#ffe1d3"),
                        list("五一广场", "国金中心", "解放西", "扬帆夜市"),
                        list("暑期夜市", "节假日夜场", "周末livehouse", "小龙虾季"),
                        list("长沙夜宵", "解放西酒吧", "五一广场美食", "长沙livehouse"),
                        list("五一广场夜生活线", "吃虾到续摊", "国金中心年轻人路线"),
                        true),
                scenes("430100",
                        scene("changsha-night", "五一广场夜生活", "吃完还能接酒吧，不浪费热闹氛围", "长沙夜生活", 8L, "五一广场/解放西", "夜宵和酒吧一线串起", "在长沙晚上想先吃再玩，怎么安排更顺", list("夜生活", "年轻人", "热闹")),
                        scene("changsha-supper", "小龙虾夜宵", "越晚越要够味、够近、够热闹", "长沙小龙虾", 1L, "扬帆夜市", "夜宵密度高", "半夜在长沙想吃小龙虾去哪里更带劲", list("夜宵", "小龙虾", "烟火")),
                        scene("changsha-friends", "朋友周末局", "拍照、聊天、吃喝都要有内容", "长沙朋友聚会", 1L, "国金中心", "一站式逛吃", "周末在长沙和朋友出去玩怎么安排", list("周末", "聚会", "打卡")),
                        scene("changsha-live", "livehouse 之前", "先吃点好吃的再去听现场", "长沙livehouse", 1L, "解放西", "吃喝到演出顺路", "在长沙晚上想去 livehouse，之前先去哪吃", list("演出", "夜晚", "路线")))
        );
        registerCity(
                cityProfile("120100", "天津", "天津", 117.2009, 39.0842,
                        "天津的舒服感在街区、相声和一顿热乎饭里",
                        "适合做老城街区消费和周末轻松路线推荐",
                        list("海河", "相声", "老城", "烟火"),
                        list("海河散步", "周末会友", "深夜热食", "亲子慢逛"),
                        list("美食", "亲子游乐", "酒吧", "健身运动"),
                        "适合轻松型和家庭型决策",
                        theme("#d7683f", "#22435a", "#fff9f4", "#ddeef6"),
                        list("五大道", "意风区", "滨江道", "古文化街"),
                        list("海河夜风", "周末相声", "亲子市集", "冬天热食"),
                        list("天津早餐", "五大道下午茶", "海河夜景", "天津朋友聚会"),
                        list("五大道慢逛", "海河到晚餐", "古文化街亲子线"),
                        true),
                scenes("120100",
                        scene("tianjin-river", "海河散步", "不赶路，想顺路找一顿舒服的饭", "天津海河散步", 1L, "海河/意风区", "散步到晚餐一线完成", "在天津想沿海河散步然后吃顿饭，怎么安排更顺", list("散步", "夜景", "松弛")),
                        scene("tianjin-friends", "周末会友", "想找一个轻松又有聊天氛围的地方", "天津朋友聚会", 1L, "五大道", "下午茶到晚餐顺路", "周末在天津和朋友见面去哪更舒服", list("周末", "会友", "轻松")),
                        scene("tianjin-family", "亲子慢逛", "带娃也能走得轻松一点", "天津亲子半日", 7L, "古文化街", "景点和吃喝不分开", "在天津带孩子半天怎么玩更省心", list("亲子", "周末", "省心")),
                        scene("tianjin-night", "深夜热食", "晚上也想来一顿热乎的", "天津夜宵", 1L, "滨江道", "热食密度更高", "晚上在天津想吃点热乎的去哪更稳", list("夜宵", "烟火", "热食")))
        );
    }

    @Override
    public List<CityOverviewDTO> listCityOverviews() {
        return profileMap.values().stream().map(this::toOverview).collect(Collectors.toList());
    }

    @Override
    public List<CityOverviewDTO> listHotCityOverviews() {
        List<CityOverviewDTO> result = new ArrayList<>();
        for (String cityCode : hotCityCodes) {
            result.add(toOverview(getCityProfile(cityCode)));
        }
        return result;
    }

    @Override
    public CityProfileDTO getCityProfile(String cityCode) {
        CityProfileDTO profile = profileMap.get(normalizeCityCode(cityCode));
        if (profile == null) {
            profile = profileMap.get(DEFAULT_CITY_CODE);
        }
        return copyProfile(profile);
    }

    @Override
    public CityProfileDTO matchCityProfile(String cityCode, String cityName) {
        String normalizedCode = normalizeCityCode(cityCode);
        if (StrUtil.isNotBlank(normalizedCode) && profileMap.containsKey(normalizedCode)) {
            return getCityProfile(normalizedCode);
        }
        String normalizedName = normalizeCityName(cityName);
        if (StrUtil.isNotBlank(normalizedName)) {
            for (CityProfileDTO profile : profileMap.values()) {
                if (normalizedName.equals(normalizeCityName(profile.getCityName()))
                        || normalizedName.equals(normalizeCityName(profile.getProvince()))) {
                    return copyProfile(profile);
                }
            }
        }
        return null;
    }

    @Override
    public List<CityScenePackDTO> listCityScenes(String cityCode) {
        List<CityScenePackDTO> scenes = sceneMap.get(normalizeCityCode(cityCode));
        if (scenes == null) {
            scenes = sceneMap.get(DEFAULT_CITY_CODE);
        }
        if (scenes == null) {
            return Collections.emptyList();
        }
        List<CityScenePackDTO> result = new ArrayList<>();
        for (CityScenePackDTO scene : scenes) {
            result.add(copyScene(scene));
        }
        return result;
    }

    @Override
    public Map<String, Object> publishCityVersion(String cityCode) {
        CityProfileDTO profile = getCityProfile(cityCode);
        Map<String, Object> payload = new HashMap<>();
        payload.put("cityCode", profile.getCityCode());
        payload.put("cityName", profile.getCityName());
        payload.put("status", "PUBLISHED");
        payload.put("publishedAt", LocalDateTime.now());
        payload.put("message", "城市运营版本已刷新，可用于首页、搜索、地图和城市馆");
        return payload;
    }

    @Override
    public MerchantCityStatsDTO getMerchantCityStats(String cityCode) {
        CityProfileDTO profile = getCityProfile(cityCode);
        List<Shop> cityShops = queryCityShops(profile);
        List<Long> shopIds = cityShops.stream().map(Shop::getId).filter(id -> id != null).collect(Collectors.toList());
        List<Voucher> vouchers = shopIds.isEmpty()
                ? Collections.emptyList()
                : voucherMapper.selectList(new LambdaQueryWrapper<Voucher>().in(Voucher::getShopId, shopIds));
        List<Long> voucherIds = vouchers.stream().map(Voucher::getId).filter(id -> id != null).collect(Collectors.toList());
        List<VoucherOrder> orders = voucherIds.isEmpty()
                ? Collections.emptyList()
                : voucherOrderMapper.selectList(new LambdaQueryWrapper<VoucherOrder>().in(VoucherOrder::getVoucherId, voucherIds));

        MerchantCityStatsDTO stats = new MerchantCityStatsDTO();
        stats.setCityCode(profile.getCityCode());
        stats.setCityName(profile.getCityName());
        stats.setShopCount(cityShops.size());
        stats.setVoucherCount(vouchers.size());
        stats.setActiveVoucherCount((int) vouchers.stream().filter(item -> item.getStatus() != null && item.getStatus() == 1).count());
        stats.setOrderCount(orders.size());
        stats.setPaidOrderCount((int) orders.stream().filter(item -> item.getStatus() != null && item.getStatus() >= 2).count());
        stats.setVerifiedOrderCount((int) orders.stream().filter(item -> item.getStatus() != null && item.getStatus() == 3).count());
        long grossPayValue = 0L;
        Map<Long, Voucher> voucherMap = vouchers.stream()
                .filter(item -> item.getId() != null)
                .collect(Collectors.toMap(Voucher::getId, item -> item, (left, right) -> left, LinkedHashMap::new));
        for (VoucherOrder order : orders) {
            Voucher voucher = voucherMap.get(order.getVoucherId());
            if (voucher != null && voucher.getPayValue() != null && order.getStatus() != null && order.getStatus() >= 2) {
                grossPayValue += voucher.getPayValue();
            }
        }
        stats.setGrossPayValue(grossPayValue);
        return stats;
    }

    private List<Shop> queryCityShops(CityProfileDTO profile) {
        String cityCode = profile.getCityCode();
        String cityName = profile.getCityName();
        String prefix = cityCode.length() >= 4 ? cityCode.substring(0, 4) : cityCode;
        LambdaQueryWrapper<Shop> wrapper = new LambdaQueryWrapper<>();
        wrapper.and(group -> group.eq(Shop::getCityCode, cityCode)
                .or()
                .likeRight(Shop::getAdcode, prefix)
                .or()
                .like(Shop::getCity, cityName)
                .or()
                .like(Shop::getAddress, cityName));
        return shopMapper.selectList(wrapper);
    }

    private void registerCity(CityProfileDTO profile, List<CityScenePackDTO> scenes) {
        profileMap.put(profile.getCityCode(), profile);
        sceneMap.put(profile.getCityCode(), scenes);
    }

    private CityOverviewDTO toOverview(CityProfileDTO profile) {
        CityOverviewDTO dto = new CityOverviewDTO();
        dto.setCityCode(profile.getCityCode());
        dto.setCityName(profile.getCityName());
        dto.setProvince(profile.getProvince());
        dto.setHeroTitle(profile.getHeroTitle());
        dto.setCityTagline(profile.getCityTagline());
        dto.setCultureTags(copyList(profile.getCultureTags()));
        dto.setFeaturedDistricts(copyList(profile.getFeaturedDistricts()));
        dto.setPrimaryCategories(copyList(profile.getPrimaryCategories()));
        dto.setHotSearches(copyList(profile.getHotSearches()));
        dto.setPriceTone(profile.getPriceTone());
        dto.setOpen(profile.getOpen());
        return dto;
    }

    private CityProfileDTO cityProfile(String cityCode, String cityName, String province,
                                       double longitude, double latitude,
                                       String heroTitle, String cityTagline,
                                       List<String> cultureTags, List<String> defaultScenes,
                                       List<String> primaryCategories, String priceTone,
                                       Map<String, String> visualTheme, List<String> featuredDistricts,
                                       List<String> seasonalHooks, List<String> hotSearches,
                                       List<String> featuredRoutes, boolean open) {
        CityProfileDTO profile = new CityProfileDTO();
        profile.setCityCode(cityCode);
        profile.setCityName(cityName);
        profile.setProvince(province);
        profile.setLongitude(longitude);
        profile.setLatitude(latitude);
        profile.setHeroTitle(heroTitle);
        profile.setCityTagline(cityTagline);
        profile.setCultureTags(cultureTags);
        profile.setDefaultScenes(defaultScenes);
        profile.setPrimaryCategories(primaryCategories);
        profile.setPriceTone(priceTone);
        profile.setVisualTheme(visualTheme);
        profile.setFeaturedDistricts(featuredDistricts);
        profile.setSeasonalHooks(seasonalHooks);
        profile.setHotSearches(hotSearches);
        profile.setFeaturedRoutes(featuredRoutes);
        profile.setOpen(open);
        return profile;
    }

    private List<CityScenePackDTO> scenes(String cityCode, CityScenePackDTO... scenes) {
        List<CityScenePackDTO> result = new ArrayList<>();
        for (CityScenePackDTO scene : scenes) {
            scene.setCityCode(cityCode);
            result.add(scene);
        }
        return result;
    }

    private CityScenePackDTO scene(String id, String title, String subtitle, String searchKeyword,
                                   Long typeId, String districtHint, String routeHint,
                                   String assistantPrompt, List<String> tags) {
        CityScenePackDTO scene = new CityScenePackDTO();
        scene.setId(id);
        scene.setTitle(title);
        scene.setSubtitle(subtitle);
        scene.setSearchKeyword(searchKeyword);
        scene.setTypeId(typeId);
        scene.setDistrictHint(districtHint);
        scene.setRouteHint(routeHint);
        scene.setAssistantPrompt(assistantPrompt);
        scene.setTags(tags);
        scene.setIcon(resolveSceneIcon(title));
        return scene;
    }

    private String resolveSceneIcon(String title) {
        if (StrUtil.contains(title, "夜")) {
            return "moon";
        }
        if (StrUtil.contains(title, "亲子")) {
            return "family";
        }
        if (StrUtil.contains(title, "火锅") || StrUtil.contains(title, "午餐") || StrUtil.contains(title, "晚餐")) {
            return "food";
        }
        if (StrUtil.contains(title, "散步") || StrUtil.contains(title, "walk")) {
            return "walk";
        }
        if (StrUtil.contains(title, "约会")) {
            return "date";
        }
        return "scene";
    }

    private Map<String, String> theme(String primary, String secondary, String surface, String badge) {
        Map<String, String> theme = new LinkedHashMap<>();
        theme.put("primary", primary);
        theme.put("secondary", secondary);
        theme.put("surface", surface);
        theme.put("badge", badge);
        return theme;
    }

    private List<String> list(String... values) {
        return new ArrayList<>(Arrays.asList(values));
    }

    private CityProfileDTO copyProfile(CityProfileDTO source) {
        CityProfileDTO target = new CityProfileDTO();
        target.setCityCode(source.getCityCode());
        target.setCityName(source.getCityName());
        target.setProvince(source.getProvince());
        target.setLongitude(source.getLongitude());
        target.setLatitude(source.getLatitude());
        target.setHeroTitle(source.getHeroTitle());
        target.setCityTagline(source.getCityTagline());
        target.setCultureTags(copyList(source.getCultureTags()));
        target.setDefaultScenes(copyList(source.getDefaultScenes()));
        target.setPrimaryCategories(copyList(source.getPrimaryCategories()));
        target.setPriceTone(source.getPriceTone());
        target.setVisualTheme(source.getVisualTheme() == null ? Collections.emptyMap() : new LinkedHashMap<>(source.getVisualTheme()));
        target.setFeaturedDistricts(copyList(source.getFeaturedDistricts()));
        target.setSeasonalHooks(copyList(source.getSeasonalHooks()));
        target.setHotSearches(copyList(source.getHotSearches()));
        target.setFeaturedRoutes(copyList(source.getFeaturedRoutes()));
        target.setOpen(source.getOpen());
        return target;
    }

    private CityScenePackDTO copyScene(CityScenePackDTO source) {
        CityScenePackDTO target = new CityScenePackDTO();
        target.setId(source.getId());
        target.setCityCode(source.getCityCode());
        target.setTitle(source.getTitle());
        target.setSubtitle(source.getSubtitle());
        target.setSearchKeyword(source.getSearchKeyword());
        target.setTypeId(source.getTypeId());
        target.setIcon(source.getIcon());
        target.setDistrictHint(source.getDistrictHint());
        target.setRouteHint(source.getRouteHint());
        target.setAssistantPrompt(source.getAssistantPrompt());
        target.setTags(copyList(source.getTags()));
        return target;
    }

    private List<String> copyList(List<String> source) {
        return source == null ? Collections.emptyList() : new ArrayList<>(source);
    }

    private String normalizeCityCode(String cityCode) {
        if (StrUtil.isBlank(cityCode)) {
            return "";
        }
        String raw = cityCode.trim();
        if (raw.length() >= 6) {
            return raw.substring(0, 4) + "00";
        }
        return raw;
    }

    private String normalizeCityName(String cityName) {
        if (StrUtil.isBlank(cityName)) {
            return "";
        }
        return cityName.trim().replace("市", "");
    }
}
