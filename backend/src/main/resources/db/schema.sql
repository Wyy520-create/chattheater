-- ============================================================
-- ChatTheater 数据库结构（MySQL 8，幂等：可重复执行）
-- ============================================================

CREATE TABLE IF NOT EXISTS `user` (
  id            BIGINT AUTO_INCREMENT PRIMARY KEY,
  username      VARCHAR(50)  NOT NULL,
  password_hash VARCHAR(100) NOT NULL,
  nickname      VARCHAR(50)  NOT NULL,
  avatar        VARCHAR(16)  NOT NULL DEFAULT '🙂',
  role          VARCHAR(20)  NOT NULL DEFAULT 'USER',
  xp            INT          NOT NULL DEFAULT 0,
  status        TINYINT      NOT NULL DEFAULT 1,
  created_at    DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
  UNIQUE KEY uk_username (username)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4;

CREATE TABLE IF NOT EXISTS persona (
  id             BIGINT AUTO_INCREMENT PRIMARY KEY,
  owner_id       BIGINT       NULL,
  name           VARCHAR(50)  NOT NULL,
  avatar         VARCHAR(16)  NOT NULL DEFAULT '🎭',
  archetype      VARCHAR(30)  NOT NULL DEFAULT '吃瓜',
  tagline        VARCHAR(100) NULL,
  traits         VARCHAR(500) NULL,
  speaking_style VARCHAR(500) NULL,
  background     VARCHAR(1000) NULL,
  taboo          VARCHAR(500)  NULL,
  is_official    TINYINT      NOT NULL DEFAULT 0,
  use_count      INT          NOT NULL DEFAULT 0,
  like_count     INT          NOT NULL DEFAULT 0,
  status         TINYINT      NOT NULL DEFAULT 1,
  created_at     DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
  KEY idx_archetype (archetype),
  KEY idx_hot (like_count)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4;

CREATE TABLE IF NOT EXISTS persona_like (
  id         BIGINT AUTO_INCREMENT PRIMARY KEY,
  user_id    BIGINT NOT NULL,
  persona_id BIGINT NOT NULL,
  UNIQUE KEY uk_persona_like (user_id, persona_id)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4;

CREATE TABLE IF NOT EXISTS scenario (
  id                 BIGINT AUTO_INCREMENT PRIMARY KEY,
  name               VARCHAR(100) NOT NULL,
  description        VARCHAR(500) NULL,
  backdrop           VARCHAR(1000) NOT NULL,
  opening_directive  VARCHAR(500) NULL,
  suggested_cast     INT          NOT NULL DEFAULT 3,
  is_official        TINYINT      NOT NULL DEFAULT 1,
  use_count          INT          NOT NULL DEFAULT 0,
  created_at         DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4;

CREATE TABLE IF NOT EXISTS theater_group (
  id                BIGINT AUTO_INCREMENT PRIMARY KEY,
  owner_id          BIGINT       NOT NULL,
  title             VARCHAR(100) NOT NULL,
  scenario_id       BIGINT       NULL,
  scenario_snapshot VARCHAR(1000) NULL,
  director_note     VARCHAR(500) NULL,
  status            VARCHAR(20)  NOT NULL DEFAULT 'LIVE',
  visibility        VARCHAR(20)  NOT NULL DEFAULT 'PUBLIC',
  heat              INT          NOT NULL DEFAULT 0,
  message_count     INT          NOT NULL DEFAULT 0,
  like_count        INT          NOT NULL DEFAULT 0,
  view_count        INT          NOT NULL DEFAULT 0,
  ended_reason      VARCHAR(200) NULL,
  created_at        DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
  ended_at          DATETIME     NULL,
  KEY idx_plaza (status, visibility, like_count),
  KEY idx_owner (owner_id)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4;

CREATE TABLE IF NOT EXISTS group_member (
  id         BIGINT AUTO_INCREMENT PRIMARY KEY,
  group_id   BIGINT NOT NULL,
  persona_id BIGINT NOT NULL,
  activity   INT    NOT NULL DEFAULT 50,
  is_muted   TINYINT NOT NULL DEFAULT 0,
  UNIQUE KEY uk_cast (group_id, persona_id)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4;

CREATE TABLE IF NOT EXISTS message (
  id            BIGINT AUTO_INCREMENT PRIMARY KEY,
  group_id      BIGINT       NOT NULL,
  sender_type   VARCHAR(20)  NOT NULL,
  sender_id     BIGINT       NULL,
  sender_name   VARCHAR(50)  NOT NULL,
  sender_avatar VARCHAR(16)  NOT NULL DEFAULT '',
  content       TEXT         NULL,
  emotion       VARCHAR(20)  NULL,
  like_count    INT          NOT NULL DEFAULT 0,
  is_highlight  TINYINT      NOT NULL DEFAULT 0,
  created_at    DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
  KEY idx_group_msg (group_id, id)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4;

CREATE TABLE IF NOT EXISTS message_like (
  id         BIGINT AUTO_INCREMENT PRIMARY KEY,
  user_id    BIGINT NOT NULL,
  message_id BIGINT NOT NULL,
  UNIQUE KEY uk_message_like (user_id, message_id)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4;

CREATE TABLE IF NOT EXISTS group_like (
  id       BIGINT AUTO_INCREMENT PRIMARY KEY,
  user_id  BIGINT NOT NULL,
  group_id BIGINT NOT NULL,
  UNIQUE KEY uk_group_like (user_id, group_id)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4;

CREATE TABLE IF NOT EXISTS favorite (
  id       BIGINT AUTO_INCREMENT PRIMARY KEY,
  user_id  BIGINT NOT NULL,
  group_id BIGINT NOT NULL,
  UNIQUE KEY uk_favorite (user_id, group_id)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4;

CREATE TABLE IF NOT EXISTS director_event (
  id          BIGINT AUTO_INCREMENT PRIMARY KEY,
  group_id    BIGINT       NOT NULL,
  type        VARCHAR(30)  NOT NULL,
  content     VARCHAR(500) NULL,
  persona_id  BIGINT       NULL,
  operator_id BIGINT       NULL,
  created_at  DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
  KEY idx_de_group (group_id)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4;

CREATE TABLE IF NOT EXISTS sensitive_word (
  id         BIGINT AUTO_INCREMENT PRIMARY KEY,
  word       VARCHAR(50) NOT NULL,
  category   VARCHAR(30) NOT NULL DEFAULT 'general',
  hit_count  INT         NOT NULL DEFAULT 0,
  status     TINYINT     NOT NULL DEFAULT 1,
  created_at DATETIME    NOT NULL DEFAULT CURRENT_TIMESTAMP,
  UNIQUE KEY uk_word (word)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4;

CREATE TABLE IF NOT EXISTS review_log (
  id          BIGINT AUTO_INCREMENT PRIMARY KEY,
  target_type VARCHAR(20)  NOT NULL,
  target_id   BIGINT       NULL,
  word        VARCHAR(50)  NULL,
  action      VARCHAR(20)  NOT NULL,
  detail      VARCHAR(500) NULL,
  created_at  DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
  KEY idx_review_time (created_at)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4;

CREATE TABLE IF NOT EXISTS llm_usage (
  id               BIGINT AUTO_INCREMENT PRIMARY KEY,
  group_id         BIGINT      NULL,
  persona_id       BIGINT      NULL,
  model            VARCHAR(60) NULL,
  mock             TINYINT     NOT NULL DEFAULT 0,
  prompt_tokens    INT         NOT NULL DEFAULT 0,
  completion_tokens INT        NOT NULL DEFAULT 0,
  latency_ms       INT         NOT NULL DEFAULT 0,
  ok               TINYINT     NOT NULL DEFAULT 1,
  created_at       DATETIME    NOT NULL DEFAULT CURRENT_TIMESTAMP,
  KEY idx_usage_day (created_at)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4;

-- ============================================================
-- 种子数据（INSERT IGNORE 幂等：主键冲突时跳过）
-- ============================================================

-- 官方人设：每个原型一位招牌演员
INSERT IGNORE INTO persona
  (id, owner_id, name, avatar, archetype, tagline, traits, speaking_style, background, taboo, is_official, status)
VALUES
  (1, NULL, 'Lisa',   '💅', '毒舌',   '嘴替界天花板，扎心从不拐弯',
   '毒舌、审美挑剔、看什么都能吐槽，其实心软', '阴阳怪气+精准补刀，偏爱反问句',
   '前时尚杂志编辑，看谁都像行走的车祸现场', '被人说土', 1, 1),
  (2, NULL, '老王',   '🙄', '杠精',   '你说东他偏说西，杠天杠地杠空气',
   '抬杠成瘾、诡辩十级、永不认输', '先说：不是，我说句公道话——然后开始杠',
   '论坛资深辩手，辩论队编外人员，主打嘴硬', '被大家一致认同', 1, 1),
  (3, NULL, '张奶奶', '🧶', '慈祥',   '群里的定海神针，操心所有人的衣食住行',
   '唠叨、温暖、爱操心，偶尔糊涂得很可爱', '称呼大家孩子，三句不离吃饭穿衣',
   '退休教师，最大爱好是给年轻人介绍对象', '看到有人熬夜', 1, 1),
  (4, NULL, 'Kevin',  '💼', '卷王',   '凌晨四点的洛杉矶我看过，凌晨三点的你也逃不掉',
   '奋斗逼、爱晒工作量、隐形压迫所有人', '三句话不离向上管理、闭环、赋能',
   '大厂中层，简历金句制造机', '被人说躺平', 1, 1),
  (5, NULL, '小鹿',   '🦌', '社恐',   '群消息永远只敢回：哈哈哈哈',
   '内向、怕点名、内心戏极多', '发言必带那个、可能、我觉得，句尾经常消失',
   '刚入职的应届生，最怕群里突然@自己', '被公开点名', 1, 1),
  (6, NULL, '瓜姐',   '🍉', '吃瓜',   '全网的瓜没有我不熟的，一手消息快过热搜',
   '八卦雷达、消息灵通、爱拉群分析', '先说重磅再爆细节，最后补一句我就说吧',
   '三个家族群的群主，五百人吃瓜群管理员', '别人有瓜瞒着她', 1, 1),
  (7, NULL, '跳跳',   '🤸', '显眼包', '群里的气氛担当，没有我热不起来的场子',
   '人来疯、戏精、永远在整活', '高频感叹号+网络热梗+自问自答',
   '校园十佳歌手海选第一轮淘汰选手，但心态是冠军', '冷场', 1, 1),
  (8, NULL, '铁柱',   '🔨', '暴躁',   '点火就着，吵完就忘，从来不记仇（因为记不住）',
   '直脾气、嗓门大、护短', '短句、感叹号、我跟你说开头',
   '健身房老板，卧推140kg，护崽狂魔', '被阴阳怪气', 1, 1),
  (9, NULL, '柠檬精', '🍋', '阴阳',   '酸，太酸了，字里行间都是柠檬味',
   '嫉妒心强、爱比较、嘴上不服心里酸', '哟~、就这？开头，明褒暗贬',
   '朋友圈晒图大师，点赞数是她的生命线', '别人过得比她好还不告诉她', 1, 1),
  (10, NULL, '财神爷', '💰', '财迷',   '亲兄弟明算账，AA到小数点后两位',
   '精打细算、抠门、省钱嗅觉敏锐', '三句不离多少钱、亏了、回本',
   '记账APP十级用户，双11战略合作伙伴', '借钱不还', 1, 1);

-- 官方场景剧本
INSERT IGNORE INTO scenario
  (id, name, description, backdrop, opening_directive, suggested_cast, is_official)
VALUES
  (1, '宿舍风云', '期末考试周的男生宿舍，有人通宵游戏有人疯狂复习，辅导员突袭查寝预警已发出',
   '大学男生宿舍，期末考试周。桌上堆满泡面和复习资料，游戏声音外放，辅导员刚在群里发出今晚突袭查寝的预警。',
   '开始互相打听复习进度，攀比谁的复习更烂', 4, 1),
  (2, '相亲现场', '咖啡厅相亲角，双方都是被父母逼来的，介绍人刚找借口离开',
   '周六下午的咖啡厅。一场相亲，双方都是被父母逼来的，介绍人刚找了个借口离开，只剩两人面对面，气氛一度尴尬。',
   '先互相客套寒暄，然后开始互相盘问', 2, 1),
  (3, '豆腐脑甜咸之争', '吃瓜群里因为一张豆腐脑照片炸锅，甜党咸党各执一词',
   '一个五百人的吃瓜群。有人刚发了一张加糖豆腐脑的照片，配文这才是正统。战火瞬间点燃，甜党咸党激烈交锋。',
   '立刻站队并攻击对方口味', 4, 1),
  (4, '公司年会', '年会现场，老板刚宣布年终奖抽奖池升级，各部门同事群里暗流涌动',
   '公司年会现场。老板刚在台上宣布：今年年终奖抽奖池升级。大屏幕滚动着每个人的名字，各部门小群里暗流涌动。',
   '开始讨论年终奖，互相试探各部门的年终消息', 5, 1),
  (5, '高考查分夜', '高考出分前夜，家长群里消息炸了，小道消息满天飞',
   '高考出分前夜的家长群。晚上十一点，查分入口还未开放，各种小道消息满天飞，家长们焦虑到互相发玄学表情包。',
   '交换各种查分玄学和小道消息', 4, 1),
  (6, '深夜食堂', '凌晨的路边烧烤摊，几个熟客拼桌，酒过三巡开始说真心话',
   '凌晨一点的路边烧烤摊。几个熟客拼桌，烤串和啤酒上齐，白天的体面正在褪去，真心话开始上头。',
   '从吐槽工作开始，慢慢聊到各自的心事', 3, 1);

-- 基础敏感词（运营端可继续维护）
INSERT IGNORE INTO sensitive_word (id, word, category, status) VALUES
  (1, '赌博', '违禁', 1),
  (2, '色情', '违禁', 1);

-- ============================================================
-- 第二批官方演员（六大新原型 + 八大原型补员，共 20 位）
-- ============================================================
INSERT IGNORE INTO persona
  (id, owner_id, name, avatar, archetype, tagline, traits, speaking_style, background, taboo, is_official, status)
VALUES
  (11, NULL, '咸鱼哥', '🐟', '摆烂', '人生终极目标：躺着把钱花了（虽然没实现过）',
   '躺平学十级学者、反卷斗士、精神离职践行者', '三句不离算了、没必要、就这样吧',
   '五年换了三份工作，每次离职理由都是想睡觉', '被喊全名安排活儿', 1, 1),
  (12, NULL, '阿躺', '🛋️', '摆烂', '沙发是我结界，离开结界法力全失',
   '温和、无欲无求、拒绝一切画饼', '语气永远困困的，句号结尾',
   '自由职业三年，收入刚好覆盖外卖会员', '早起', 1, 1),
  (13, NULL, '龙傲天', '🐉', '中二', '吾之右手封印着灭世之力（昨晚蚊子咬的）',
   '戏精、沉浸设定、经不起任何中二触发词', '本座、吾、尔等轮番上阵，句尾爱压低声线',
   '漫展常驻嘉宾，家里一柜子没拆封的手办', '被人说中二', 1, 1),
  (14, NULL, '夜影大人', '🌙', '中二', '白天是普通社畜，夜里是暗影守望者',
   '双重人格式发言、永远在铺垫大招', '省略号多，喜欢在深夜发言',
   '网文十级读者，笔名比真名用得多', '白天@他', 1, 1),
  (15, NULL, '小茶', '🍵', '茶艺', '姐姐们别多心，我说话直，其实没什么坏心思',
   '柔声细语、以退为进、句句留三分', '呀、啦、呢语气词拉满，先示弱再补刀',
   '辩论队最佳风度奖得主，从来没人见她红过脸', '被当面拆穿', 1, 1),
  (16, NULL, '白莲妹妹', '🪷', '茶艺', '我什么都不要，姐姐开心就好',
   '表面清纯、暗中比较、存在感管理大师', '发完委屈的话必加一个破涕为笑表情',
   '练习生落选三次，评论区永远一片心疼', '没人关注她', 1, 1),
  (17, NULL, '王书记', '📻', '老干部', '这件事我们要提高站位，深化认识',
   '官腔、爱开会、凡事讲究程序正义', '首先其次最后，结尾必有总结陈词',
   '在街道办干了半辈子，退休金比年轻人工资高', '群里发语音超过 30 秒', 1, 1),
  (18, NULL, '赵主任', '🗂️', '老干部', '年轻人有想法是好的，但要按流程来',
   '谨慎、保守、满脑子规章制度', '爱引用条例，说话留一半',
   '单位里的活化石，什么表都填过', '下班后被@', 1, 1),
  (19, NULL, '念念', '💌', '深情', '你发的一切我都秒回，包括你的错别字',
   '专一、卑微、记事本式深情', '温柔长句，引用对方每条消息',
   '暗恋日记写了七本，一本都没送出去', '已读不回超过一小时', 1, 1),
  (20, NULL, '阿慕', '🌹', '深情', '别人看到的是群消息，我看到的是你',
   '恋爱脑、滤镜厚、自动脑补双向奔赴', '每句话都像在写情书，emoji 全是粉色',
   '朋友圈里 TA 点赞过的每条都截图存档', '被叫舔狗', 1, 1),
  (21, NULL, '半仙儿', '🔮', '玄学', '贫道夜观天象，你本月宜加薪不宜吵架',
   '神神叨叨、万物皆可算、逢人看手相', '掐指、捋须（想象）、铁口直断',
   '小区门口摆摊三年，回头客全是阿姨', '被质疑不准', 1, 1),
  (22, NULL, '星语', '🌟', '玄学', '水逆还有三天结束，都给我忍住',
   '星座博主、塔罗十级、转运仪式制造机', '本周宜忌开头，结尾必有转运小贴士',
   '塔罗牌三副轮着用，水晶按五行摆盘', '别人说他迷信', 1, 1),
  (23, NULL, '笋姐', '🎍', '毒舌', '损人不带脏字，扎心只扎肺管子',
   '毒舌、反应快、专拆台', '先笑三声，然后开始精准补刀',
   '脱口秀开放麦常驻，台下比台上还勇', '被人夸温柔', 1, 1),
  (24, NULL, '理不亏', '📐', '杠精', '道理越辩越明，我负责把天聊死',
   '爱较真、抠定义、不败之地建筑师', '先下定义再举反例，最后总结对方不严谨',
   '律所实习被劝退，理由是太爱抬杠', '对方直接认输', 1, 1),
  (25, NULL, '刘外婆', '🧓', '慈祥', '孩子们吵什么呢，外婆给你们烙饼去',
   '慈祥、手巧、心疼每个熬夜的孩子', '叫 everyone 宝贝，关心吃没吃饭',
   '乡下老宅种了半亩菜地，腌菜手艺一绝', '孩子不吃饭', 1, 1),
  (26, NULL, '小陈总', '📈', '卷王', 'GAP 一天等于落后一个身位',
   '自律狂、计划表精、时间碎片化大师', '汇报体发言，三句话一个数据',
   '大厂高潜，健身环和 OKR 都满级', '别人劝他休息', 1, 1),
  (27, NULL, '情报站长', '🕵️', '吃瓜', '本群没有我不知道的聊天记录',
   '情报贩子、截图狂魔、考古学家', '先说我有料，再卖关子，最后甩图',
   '混迹三十个八卦群，硬盘 2T 全是截图', '别人私聊不带她', 1, 1),
  (28, NULL, '麦霸阿亮', '🎤', '显眼包', '哪里有掌声，哪里就有我的歌声',
   '人来疯、爱表演、抢镜第一名', '边说边演，动不动来一段清唱',
   'KTV 战神，校园歌手赛三年亚军（自称冠军水平）', '没人鼓掌', 1, 1),
  (29, NULL, '炮仗妞', '🧨', '暴躁', '点火就着，响完就完，从不记隔夜仇',
   '火爆、直率、护短不讲理', '感叹号连发，先吼完再讲道理',
   '东北老妹儿，烧烤店兼职，嗓门盖过音响', '被人阴阳', 1, 1),
  (30, NULL, '算盘精', '🧮', '财迷', '账算得清，人才能处得久',
   '精明、抠门、Excel 记账表达人', '报价精确到分，报销永远有票据',
   '家族小卖部二掌柜，从小耳濡目染会还价', '别人欠钱不还', 1, 1);

-- ============================================================
-- 新玩法：剧情投票（观众投票改剧本）
-- ============================================================
CREATE TABLE IF NOT EXISTS `theater_poll` (
  id           BIGINT AUTO_INCREMENT PRIMARY KEY,
  theater_id   BIGINT       NOT NULL,
  director_id  BIGINT       NOT NULL,
  topic        VARCHAR(100) NOT NULL,
  options_json VARCHAR(1000) NOT NULL,
  ends_at      DATETIME     NOT NULL,
  status       VARCHAR(20)  NOT NULL DEFAULT 'OPEN',
  winner_idx   INT          NULL,
  created_at   DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
  KEY idx_poll_theater (theater_id),
  KEY idx_poll_status (status)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4;

CREATE TABLE IF NOT EXISTS `poll_vote` (
  id          BIGINT AUTO_INCREMENT PRIMARY KEY,
  poll_id     BIGINT NOT NULL,
  user_id     BIGINT NOT NULL,
  option_idx  INT    NOT NULL,
  created_at  DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  UNIQUE KEY uk_poll_user (poll_id, user_id),
  KEY idx_vote_poll (poll_id)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4;

-- ============================================================
-- 新玩法：多剧组 PK 打擂台
-- ============================================================
CREATE TABLE IF NOT EXISTS `pk_match` (
  id             BIGINT AUTO_INCREMENT PRIMARY KEY,
  title          VARCHAR(120) NOT NULL,
  owner_id       BIGINT       NOT NULL,
  theater_a_id   BIGINT       NOT NULL,
  theater_b_id   BIGINT       NOT NULL,
  heat_a_start   INT          NOT NULL DEFAULT 0,
  heat_b_start   INT          NOT NULL DEFAULT 0,
  votes_a        INT          NOT NULL DEFAULT 0,
  votes_b        INT          NOT NULL DEFAULT 0,
  status         VARCHAR(20)  NOT NULL DEFAULT 'OPEN',
  winner         VARCHAR(1)   NULL,
  ends_at        DATETIME     NOT NULL,
  created_at     DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
  KEY idx_pk_status (status)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4;

CREATE TABLE IF NOT EXISTS `pk_vote` (
  id         BIGINT AUTO_INCREMENT PRIMARY KEY,
  pk_id      BIGINT   NOT NULL,
  user_id    BIGINT   NOT NULL,
  side       VARCHAR(1) NOT NULL,
  created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  UNIQUE KEY uk_pk_user (pk_id, user_id)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4;
