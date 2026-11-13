package com.dichgame.app

/**
 * Gói thuật ngữ Việt hoá cho Kho game. Mỗi gói là các cặp "từ gốc = tiếng Việt" được thêm vào Từ điển riêng
 * của hồ sơ game, để bản dịch AI dùng nhất quán các từ hay gặp (nhiệm vụ, túi đồ, thuật ngữ riêng của game...).
 *
 * Ba lớp, riêng cho game đứng trước: (1) riêng từng game hoặc series, (2) theo thể loại, (3) chung cho mọi game.
 * Đây là gợi ý do người làm app tự soạn, không phải bản dịch chính thức của nhà phát hành; người dùng sửa
 * được trong Cài đặt › Từ điển riêng. Áp dụng cho cả dịch AI lẫn dịch trên máy.
 */
object VietHoa {
    internal fun terms(s: String): List<Pair<String, String>> =
        s.trim().lines().mapNotNull { l ->
            val i = l.indexOf('=')
            if (i <= 0) null else l.substring(0, i).trim() to l.substring(i + 1).trim()
        }.filter { it.first.isNotEmpty() && it.second.isNotEmpty() }

    /** Thuật ngữ chung, dùng cho mọi game. */
    val COMMON = terms("""
Quest = Nhiệm vụ
Main Quest = Nhiệm vụ chính
Side Quest = Nhiệm vụ phụ
Objective = Mục tiêu
Inventory = Túi đồ
Equipment = Trang bị
Skill = Kỹ năng
Skill Tree = Cây kỹ năng
Level Up = Lên cấp
Checkpoint = Điểm lưu
Save Game = Lưu game
Load Game = Tải game
New Game = Chơi mới
Continue = Tiếp tục
Resume = Tiếp tục
Quit = Thoát
Settings = Cài đặt
Difficulty = Độ khó
Map = Bản đồ
Fast Travel = Dịch chuyển nhanh
Loot = Chiến lợi phẩm
Boss = Trùm
Health = Máu
Stamina = Thể lực
Damage = Sát thương
Upgrade = Nâng cấp
Crafting = Chế tạo
Cooldown = Thời gian hồi chiêu
Game Over = Thua cuộc
Autosave = Tự động lưu
Mission Complete = Hoàn thành nhiệm vụ
""")

    /** Gói theo thể loại: (các từ khoá có trong chuỗi thể loại, thuật ngữ). */
    private val GENRES: List<Pair<List<String>, List<Pair<String, String>>>> = listOf(
        listOf("bóng đá") to terms("""
Offside = Việt vị
Corner Kick = Phạt góc
Free Kick = Đá phạt
Penalty = Phạt đền
Yellow Card = Thẻ vàng
Red Card = Thẻ đỏ
Substitution = Thay người
Half Time = Hết hiệp một
Full Time = Hết giờ
Kick Off = Giao bóng
Goalkeeper = Thủ môn
Career Mode = Chế độ sự nghiệp
"""),
        listOf("bóng rổ") to terms("""
Rebound = Bắt bóng bật bảng
Assist = Kiến tạo
Steal = Cướp bóng
Block = Chắn bóng
Free Throw = Ném phạt
Three-Pointer = Ném ba điểm
Timeout = Hội ý
Foul = Phạm lỗi
"""),
        listOf("đua xe") to terms("""
Lap = Vòng đua
Pit Stop = Vào pit
Qualifying = Vòng phân hạng
Starting Grid = Vạch xuất phát
Tuning = Độ xe
Horsepower = Mã lực
Traction = Độ bám đường
Gear = Số
Championship = Giải vô địch
"""),
        listOf("đối kháng") to terms("""
Combo = Combo
Special Move = Chiêu đặc biệt
Super Art = Chiêu tối thượng
Round = Hiệp
Training Mode = Chế độ luyện tập
Block = Đỡ đòn
Throw = Đòn quăng
Parry = Gạt đòn
Gauge = Thanh năng lượng
K.O. = Hạ gục
"""),
        listOf("kinh dị") to terms("""
Safe Room = Phòng an toàn
Ammo = Đạn
Flashlight = Đèn pin
Hide = Trốn
Puzzle = Câu đố
Key Item = Vật phẩm quan trọng
"""),
        listOf("sinh tồn") to terms("""
Scavenge = Lục lọi
Infected = Kẻ nhiễm bệnh
Shelter = Nơi trú ẩn
Hunger = Đói
Thirst = Khát
Craft = Chế tạo
"""),
        listOf("bắn súng") to terms("""
Ammo = Đạn
Reload = Nạp đạn
Headshot = Bắn trúng đầu
Cover = Nấp
Grenade = Lựu đạn
Scope = Ống ngắm
Magazine = Hộp đạn
"""),
        listOf("nhập vai") to terms("""
Party = Đội hình
Companion = Đồng đội
Class = Lớp nhân vật
Spell = Phép thuật
Potion = Thuốc
Dungeon = Hầm ngục
Merchant = Thương nhân
Guild = Bang hội
Faction = Phe phái
Reputation = Danh tiếng
Experience Points = Điểm kinh nghiệm
Gold = Vàng
"""),
        listOf("săn quái vật") to terms("""
Monster = Quái vật
Hunt = Cuộc săn
Hunter Rank = Hạng thợ săn
Carve = Xẻ thịt
Material = Nguyên liệu
Armor = Giáp
Wyvern = Phi long
"""),
        listOf("hắc ám") to terms("""
Bonfire = Lửa trại
Estus Flask = Bình Estus
Souls = Linh hồn
YOU DIED = BẠN ĐÃ CHẾT
Poise = Độ vững
Parry = Gạt đòn
Backstab = Đâm lén
Covenant = Giao ước
"""),
        listOf("platform") to terms("""
Jump = Nhảy
Collectible = Vật phẩm sưu tầm
Power-up = Vật phẩm tăng sức mạnh
Lives = Mạng
Level = Màn chơi
"""),
    )

    /** Gói riêng cho từng game hoặc series: (các từ khoá có trong tên game viết thường, thuật ngữ). */
    private val SPECIFIC: List<Pair<List<String>, List<Pair<String, String>>>> = listOf(
        listOf("elden ring") to terms("""
Site of Grace = Điểm Ban Phước
Runes = Rune
Great Rune = Đại Rune
Spirit Ash = Tro Linh Hồn
Tarnished = Tarnished
Lands Between = Vùng Đất Giữa
Erdtree = Cây Erd
Demigod = Á thần
Remembrance = Di vật tưởng niệm
Flask of Crimson Tears = Bình Nước Mắt Đỏ
Ashes of War = Tro Chiến Tranh
Smithing Stone = Đá rèn
"""),
        listOf("dark souls") to terms("""
Ashen One = Kẻ Tro Tàn
Firelink Shrine = Đền Firelink
Lords of Cinder = Các Chúa Tro
Embers = Tàn lửa
Estus Flask = Bình Estus
"""),
        listOf("sekiro") to terms("""
Posture = Thế thủ
Deathblow = Đòn kết liễu
Resurrection = Hồi sinh
Sculptor's Idol = Tượng điêu khắc
Shinobi = Shinobi
Prosthetic = Cánh tay giả
"""),
        listOf("god of war") to terms("""
Kratos = Kratos
Atreus = Atreus
Leviathan Axe = Rìu Leviathan
Blades of Chaos = Lưỡi kiếm Hỗn Mang
Spartan Rage = Cuồng nộ Sparta
Rune Attack = Chiêu Rune
Hacksilver = Bạc vụn
Draugr = Draugr
Realm = Cõi
"""),
        listOf("cyberpunk") to terms("""
Eddies = Eddie
Netrunner = Netrunner
Braindance = Braindance
Cyberware = Cyberware
Night City = Night City
Fixer = Fixer
Gig = Phi vụ
Ripperdoc = Bác sĩ cấy ghép
Quickhack = Quickhack
Choom = Choom
"""),
        listOf("witcher") to terms("""
Witcher = Witcher
Signs = Dấu ấn
Gwent = Gwent
Crowns = Crown
Mutagen = Đột biến tố
Monster Contract = Hợp đồng quái vật
Witcher Senses = Giác quan Witcher
Bestiary = Sách quái vật
"""),
        listOf("skyrim") to terms("""
Dragonborn = Dovahkiin
Shout = Long Ngữ
Thu'um = Thu'um
Soul Gem = Đá linh hồn
Perk = Đặc quyền
Smithing = Rèn đúc
Enchanting = Phù phép
Alchemy = Giả kim
Sneak = Lẻn
"""),
        listOf("fallout") to terms("""
Pip-Boy = Pip-Boy
Vault = Hầm trú ẩn
Wasteland = Hoang mạc
Caps = Nắp chai
Settlement = Khu định cư
Power Armor = Giáp trợ lực
Radiation = Phóng xạ
Stimpak = Stimpak
Super Mutant = Siêu đột biến
Brotherhood of Steel = Hội Huynh Đệ Thép
"""),
        listOf("resident evil") to terms("""
Herb = Thảo dược
Green Herb = Thảo dược xanh
First Aid Spray = Bình xịt sơ cứu
Typewriter = Máy đánh chữ
Ink Ribbon = Ruy băng mực
Safe Room = Phòng an toàn
Umbrella Corporation = Tập đoàn Umbrella
Raccoon City = Raccoon City
Item Box = Rương đồ
"""),
        listOf("red dead") to terms("""
Dead Eye = Dead Eye
Gang = Băng đảng
Camp = Trại
Bounty = Tiền truy nã
Honor = Danh dự
Outlaw = Kẻ ngoài vòng pháp luật
Lawman = Cảnh sát trưởng
Saloon = Quán rượu
Wanted = Bị truy nã
"""),
        listOf("grand theft auto") to terms("""
Wanted Level = Mức truy nã
Los Santos = Los Santos
Heist = Phi vụ cướp
Special Ability = Khả năng đặc biệt
Rampage = Cơn thịnh nộ
"""),
        listOf("zelda") to terms("""
Link = Link
Hyrule = Hyrule
Rupee = Rupee
Korok = Korok
Shrine = Đền thờ
Sheikah Slate = Phiến đá Sheikah
Divine Beast = Thần thú
Master Sword = Thanh kiếm Master
Heart Container = Bình trái tim
Stamina Vessel = Bình thể lực
Ultrahand = Ultrahand
Fuse = Kết hợp
Ascend = Xuyên trần
Recall = Hồi tố
Zonai = Zonai
Triforce = Triforce
Bokoblin = Bokoblin
"""),
        listOf("pokémon", "pokemon") to terms("""
Pokémon = Pokémon
Trainer = Huấn luyện viên
Gym = Đấu trường
Poké Ball = Poké Ball
Tera Type = Hệ Tera
Terastallize = Tera hoá
Evolve = Tiến hoá
Move = Chiêu thức
Ability = Đặc tính
Raid Battle = Trận Raid
Pokédex = Pokédex
Nature = Tính cách
"""),
        listOf("persona") to terms("""
Persona = Persona
Phantom Thieves = Phantom Thieves
Palace = Cung điện
Confidant = Confidant
Metaverse = Metaverse
Shadow = Bóng
Social Link = Social Link
Arcana = Arcana
Mementos = Mementos
Calling Card = Thiệp báo trước
Baton Pass = Chuyền gậy
All-Out Attack = Tổng tấn công
Velvet Room = Phòng Nhung
Fusion = Dung hợp
Tartarus = Tartarus
Dark Hour = Giờ Tối
"""),
        listOf("spider-man") to terms("""
Spider-Sense = Giác quan nhện
Web-Swinging = Đu tơ
Suit = Bộ đồ
Gadget = Thiết bị
Focus = Tập trung
Finisher = Đòn kết thúc
Tech Parts = Linh kiện công nghệ
Symbiote = Symbiote
"""),
        listOf("last of us") to terms("""
Clicker = Clicker
Infected = Kẻ nhiễm bệnh
Cordyceps = Nấm Cordyceps
Fireflies = Đom Đóm
FEDRA = FEDRA
Shiv = Dao nhọn tự chế
Listen Mode = Chế độ lắng nghe
Supplements = Thuốc bổ
Bloater = Bloater
Runner = Runner
Stalker = Stalker
Shambler = Shambler
"""),
        listOf("horizon") to terms("""
Aloy = Aloy
Machine = Cỗ máy
Focus = Focus
Override = Chiếm quyền điều khiển
Tallneck = Tallneck
Nora = Nora
Banuk = Banuk
Carja = Carja
"""),
        listOf("baldur's gate") to terms("""
Tadpole = Ấu trùng
Illithid = Illithid
Mind Flayer = Mind Flayer
Short Rest = Nghỉ ngắn
Long Rest = Nghỉ dài
Saving Throw = Cứu nguy
Ability Check = Kiểm tra khả năng
Advantage = Lợi thế
Disadvantage = Bất lợi
Spell Slot = Ô phép
Concentration = Tập trung
Initiative = Khởi xướng
Bonus Action = Hành động phụ
Reaction = Phản ứng
Armour Class = Chỉ số giáp
Approval = Cảm tình
Wizard = Pháp sư
Cleric = Tu sĩ
Rogue = Đạo tặc
Fighter = Chiến binh
Ranger = Kiểm lâm
Paladin = Hiệp sĩ thánh
"""),
        listOf("final fantasy") to terms("""
Limit Break = Limit Break
Materia = Materia
Summon = Triệu hồi
Chocobo = Chocobo
Moogle = Moogle
Gil = Gil
Phoenix Down = Lông Phượng Hoàng
Ether = Ether
Mako = Mako
Eikon = Eikon
"""),
        listOf("monster hunter") to terms("""
Palico = Palico
Hunter's Guild = Hội Thợ săn
Handler = Trợ lý
Wyvern = Phi long
Hunting Horn = Kèn săn
Great Sword = Đại kiếm
Long Sword = Thái đao
Gunlance = Thương súng
Insect Glaive = Gậy côn trùng
Slinger = Súng cao su
"""),
        listOf("mortal kombat") to terms("""
Fatality = Fatality
Brutality = Brutality
Krushing Blow = Krushing Blow
Kameo = Kameo
Kombat League = Kombat League
Fatal Blow = Đòn chí mạng
"""),
        listOf("tekken") to terms("""
Rage Art = Rage Art
Rage Drive = Rage Drive
Heat = Heat
Juggle = Tâng đối thủ
Sidestep = Né ngang
Wall Carry = Dồn vào tường
"""),
        listOf("street fighter") to terms("""
Drive Gauge = Thanh Drive
Drive Impact = Drive Impact
Drive Rush = Drive Rush
Perfect Parry = Gạt đòn hoàn hảo
Super Art = Super Art
World Tour = World Tour
Fighting Ground = Fighting Ground
Battle Hub = Battle Hub
"""),
    )

    /** Gói riêng cho game này (không tính gói thể loại và gói chung). */
    fun specificFor(name: String): List<Pair<String, String>> {
        val n = name.lowercase()
        return (SPECIFIC + VietHoaPlus.SPECIFIC).filter { (keys, _) -> keys.any { n.contains(it) } }.flatMap { it.second }
    }

    /** Thẻ nhân vật có sẵn của game (các dòng "Tên = mô tả; nhịp: ..."). */
    fun castFor(name: String): List<String> = VietHoaPlus.castFor(name)

    /** Bộ nhớ câu thoại có sẵn: thông báo, tiêu đề, menu chung cộng với riêng game (câu gốc, bản Việt hoá). */
    fun linesFor(name: String): List<Pair<String, String>> = VietHoaPlus.GENERIC_LINES + VietHoaPlus.specificLines(name)

    private fun genreTermsFor(genre: String): List<Pair<String, String>> {
        val g = genre.lowercase()
        return GENRES.filter { (keys, _) -> keys.any { g.contains(it) } }.flatMap { it.second }
    }

    /** Toàn bộ thuật ngữ cho game, không trùng từ gốc: riêng game, rồi thể loại, rồi chung. */
    fun termsFor(name: String, genre: String = Catalog.genreOf(name)): List<Pair<String, String>> {
        val seen = HashSet<String>()
        val out = ArrayList<Pair<String, String>>()
        for (t in specificFor(name) + genreTermsFor(genre) + COMMON) {
            if (seen.add(t.first.lowercase())) out.add(t)
        }
        return out
    }

    /** Có gói riêng cho game này không (ngoài gói thể loại và gói chung). */
    fun hasSpecific(name: String): Boolean = specificFor(name).isNotEmpty()

    /** Số thuật ngữ theo từng lớp, để hiện trong màn hình chi tiết: (riêng, thể loại, chung). */
    fun counts(name: String, genre: String = Catalog.genreOf(name)): Triple<Int, Int, Int> {
        val all = termsFor(name, genre)
        val spec = specificFor(name).map { it.first.lowercase() }.toSet()
        val gen = genreTermsFor(genre).map { it.first.lowercase() }.toSet()
        var a = 0; var b = 0; var c = 0
        for (t in all) {
            val k = t.first.lowercase()
            if (k in spec) a++ else if (k in gen) b++ else c++
        }
        return Triple(a, b, c)
    }

    /** Thêm các thuật ngữ còn thiếu vào từ điển (chuỗi nhiều dòng "gốc = tiếng Việt"), giữ nguyên các dòng đã có. */
    fun merge(existing: String, add: List<Pair<String, String>>): String {
        val have = existing.lines().mapNotNull { l -> l.substringBefore('=', "").trim().lowercase().takeIf { it.isNotEmpty() } }.toHashSet()
        val sb = StringBuilder(existing.trimEnd())
        for ((k, v) in add) {
            if (k.lowercase() in have) continue
            if (sb.isNotEmpty()) sb.append('\n')
            sb.append(k).append(" = ").append(v)
        }
        return sb.toString()
    }
}
