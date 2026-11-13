package com.dichgame.app

/**
 * Phần mở rộng của gói Việt hoá có sẵn (xem VietHoa.kt): thuật ngữ cho thêm nhiều game và series, thẻ nhân vật
 * (giọng nói, xưng hô, nhịp) và bộ nhớ câu thoại cho các dòng thông báo, tiêu đề, menu hay gặp trên màn hình.
 *
 * Tất cả do người làm app tự soạn theo cách gọi phổ biến của cộng đồng game thủ Việt, KHÔNG phải bản dịch chính thức
 * của nhà phát hành và không chép lời thoại của game nào. Tên riêng thường được giữ nguyên gốc. Người dùng sửa hoặc
 * ghi đè được trong Cài đặt (từ điển riêng, thẻ nhân vật, nhập gói).
 */
internal object VietHoaPlus {
    private fun terms(s: String) = VietHoa.terms(s)
    private fun pairs(s: String): List<Pair<String, String>> = s.trim().lines().mapNotNull { l ->
        val i = l.indexOf(" = ")
        if (i <= 0) null else l.substring(0, i).trim() to l.substring(i + 3).trim()
    }.filter { it.first.isNotEmpty() && it.second.isNotEmpty() }

    val SPECIFIC: List<Pair<List<String>, List<Pair<String, String>>>> = listOf(
        listOf("assassin's creed") to terms("""
Animus = Animus
Assassin = Sát thủ
Templar = Hiệp sĩ Templar
Hidden Blade = Lưỡi dao ẩn
Eagle Vision = Tầm nhìn Đại bàng
Leap of Faith = Cú nhảy niềm tin
Synchronization = Đồng bộ
Desynchronized = Mất đồng bộ
Brotherhood = Hội Huynh đệ
Hidden Ones = Những Kẻ Ẩn Mình
Raven Clan = Thị tộc Quạ
Longship = Thuyền dài
"""),
        listOf("bloodborne") to terms("""
Hunter = Thợ săn
Hunter's Dream = Giấc mơ Thợ săn
Blood Echoes = Dư âm Máu
Healing Church = Giáo hội Chữa lành
Great One = Đấng Vĩ đại
Blood Vial = Ống máu
Quicksilver Bullet = Đạn thủy ngân
Chalice Dungeon = Hầm Chén Thánh
Yharnam = Yharnam
"""),
        listOf("wukong") to terms("""
Destined One = Thiên Mệnh Nhân
Sun Wukong = Tôn Ngộ Không
Wukong = Ngộ Không
Journey to the West = Tây Du Ký
Keeper's Shrine = Miếu Thổ Địa
Ruyi Jingu Bang = Như Ý Kim Cô Bổng
Gourd = Hồ lô
Yaoguai = Yêu quái
Heavenly Court = Thiên đình
Jade Emperor = Ngọc Hoàng
"""),
        listOf("death stranding") to terms("""
Porter = Người giao hàng
Timefall = Mưa Thời Gian
Chiral Network = Mạng Chiral
Bridge Baby = Bé cầu nối
Cargo = Hàng hóa
Beach = Bãi Biển
Likes = Lượt thích
"""),
        listOf("devil may cry") to terms("""
Devil Trigger = Devil Trigger
Devil Breaker = Devil Breaker
Red Orbs = Cầu đỏ
Devil Arm = Vũ khí quỷ
Style Rank = Hạng phong cách
"""),
        listOf("diablo") to terms("""
Sanctuary = Sanctuary
Hell = Địa ngục
Waypoint = Điểm dịch chuyển
Nightmare Dungeon = Hầm ngục Ác mộng
Legendary = Huyền thoại
Unique = Độc nhất
Paragon = Paragon
Obols = Obol
"""),
        listOf("kakarot", "dragon ball") to terms("""
Senzu Bean = Đậu Thần
Dragon Ball = Ngọc Rồng
Shenron = Rồng Thần
Saiyan = Saiyan
Super Saiyan = Super Saiyan
Instant Transmission = Dịch chuyển tức thời
Spirit Bomb = Quả cầu Genki
Ki = Ki
"""),
        listOf("dragon quest") to terms("""
Hero = Dũng sĩ
Slime = Slime
Mini Medal = Huy chương nhỏ
Metal Slime = Metal Slime
Erdrea = Erdrea
"""),
        listOf("tsushima") to terms("""
Ghost = Bóng Ma
Samurai = Samurai
Mongol = Mông Cổ
Standoff = Đối đầu
Resolve = Ý chí
Stance = Thế kiếm
Fox Den = Hang cáo
Hot Spring = Suối nước nóng
"""),
        listOf("hades") to terms("""
Boon = Ân huệ
Keepsake = Kỷ vật
Underworld = Địa phủ
Mirror of Night = Gương Màn Đêm
House of Hades = Dinh thự của Hades
Olympus = Olympus
Elysium = Elysium
Tartarus = Tartarus
"""),
        listOf("halo") to terms("""
Spartan = Spartan
Covenant = Covenant
Master Chief = Master Chief
Forerunner = Forerunner
Banished = Banished
Energy Sword = Kiếm năng lượng
Grappleshot = Móc câu
"""),
        listOf("hollow knight") to terms("""
Geo = Geo
Soul = Linh hồn
Charm = Bùa
Dream Nail = Đinh Mộng
Bench = Ghế nghỉ
Stag Station = Trạm Hươu
Hallownest = Hallownest
"""),
        listOf("kingdom hearts") to terms("""
Keyblade = Keyblade
Heartless = Heartless
Nobody = Nobody
Munny = Munny
Gummi Ship = Gummi Ship
Organization XIII = Tổ chức XIII
Dream Eater = Dream Eater
Paopu Fruit = Quả Paopu
"""),
        listOf("lies of p") to terms("""
Ergo = Ergo
Stargazer = Stargazer
Legion Arm = Cánh tay Legion
Perfect Guard = Đỡ hoàn hảo
Fable Art = Chiêu Ngụ Ngôn
Pulse Cell = Tế bào Xung
Puppet = Con rối
"""),
        listOf("mass effect") to terms("""
Normandy = Normandy
Reaper = Reaper
Citadel = Citadel
Spectre = Spectre
Biotic = Biotic
Omni-tool = Omni-tool
Paragon = Chính nghĩa
Renegade = Nổi loạn
Council = Hội đồng
"""),
        listOf("metal gear") to terms("""
Big Boss = Big Boss
Cardboard Box = Hộp các-tông
CQC = CQC
Codec = Codec
Camouflage = Ngụy trang
Stamina = Thể lực
"""),
        listOf("nier") to terms("""
YoRHa = YoRHa
Android = Android
Machine Lifeform = Sinh thể máy
Pod = Pod
Resistance = Quân kháng chiến
Plug-in Chip = Chip cắm
Bunker = Boongke
"""),
        listOf("nioh") to terms("""
Yokai = Yêu quái
Amrita = Amrita
Guardian Spirit = Linh hồn hộ mệnh
Ki Pulse = Xung Ki
Kodama = Kodama
Shrine = Đền thờ
Onmyo Magic = Âm Dương thuật
Ninjutsu = Nhẫn thuật
Yokai Shift = Hóa yêu
"""),
        listOf("silent hill") to terms("""
Silent Hill = Silent Hill
Pyramid Head = Pyramid Head
Fog = Sương mù
Flashlight = Đèn pin
Health Drink = Nước hồi phục
First Aid Kit = Hộp sơ cứu
Brookhaven Hospital = Bệnh viện Brookhaven
Toluca Prison = Nhà tù Toluca
Lakeview Hotel = Khách sạn Lakeview
"""),
        listOf("jedi") to terms("""
Force = Thần lực
Jedi = Jedi
Lightsaber = Kiếm ánh sáng
Padawan = Padawan
Empire = Đế chế
Meditation Point = Điểm thiền
Dark Side = Mặt tối
Light Side = Mặt sáng
Holocron = Holocron
Blaster = Súng blaster
"""),
        listOf("starfield") to terms("""
Constellation = Constellation
Settled Systems = Các Hệ Định Cư
Freestar Collective = Liên minh Freestar
Artifact = Cổ vật
Grav Jump = Nhảy trọng lực
Outpost = Tiền đồn
Spacesuit = Bộ đồ vũ trụ
Scanner = Máy quét
Starborn = Starborn
"""),
        listOf("stardew") to terms("""
Pelican Town = Thị trấn Pelican
Community Center = Trung tâm Cộng đồng
Junimo = Junimo
Joja = Joja
Fertilizer = Phân bón
Sprinkler = Vòi tưới
Skull Cavern = Hang Đầu Lâu
Bouquet = Bó hoa
Mermaid Pendant = Mặt dây nàng tiên cá
"""),
        listOf("uncharted") to terms("""
Treasure = Kho báu
Fortune Hunter = Thợ săn kho báu
Grapple = Móc câu
Journal = Nhật ký
Libertalia = Libertalia
"""),
        listOf("yakuza") to terms("""
Yakuza = Yakuza
Tojo Clan = Hội Tojo
Omi Alliance = Liên minh Omi
Job = Nghề
Drink Link = Drink Link
Sujimon = Sujimon
Substory = Chuyện bên lề
Personality = Tính cách
Kamurocho = Kamurocho
Ijincho = Ijincho
"""),
        listOf("control") to terms("""
Bureau = Cục
Oldest House = Ngôi Nhà Cổ Nhất
Hiss = Hiss
Service Weapon = Vũ khí Phục vụ
Object of Power = Vật Quyền Năng
Altered Item = Vật Biến Dị
Astral Plane = Cõi Tinh Giới
Director = Giám đốc
"""),
        listOf("alan wake") to terms("""
Dark Place = Chốn Tối
Cult of the Tree = Giáo phái Cây
Mind Place = Tâm Cảnh
Case Board = Bảng vụ án
Words of Power = Lời Quyền Năng
"""),
        listOf("dead space") to terms("""
Necromorph = Necromorph
Marker = Dấu
Plasma Cutter = Súng cắt plasma
RIG = RIG
Stasis = Làm chậm
Kinesis = Dịch vật
Bench = Bàn chế tạo
Power Node = Nút năng lượng
"""),
        listOf("hogwarts") to terms("""
Spell = Phép
Wand = Đũa phép
Room of Requirement = Phòng Yêu Cầu
Unforgivable Curse = Lời Nguyền Không Thể Tha Thứ
Sorting Hat = Nón Phân Loại
Floo Powder = Bột Floo
Muggle = Muggle
Potion = Thuốc
Broom = Chổi bay
Merlin Trial = Thử thách Merlin
Field Guide Page = Trang sổ tay
"""),
        listOf("armored core") to terms("""
Rubicon = Rubicon
Coral = Coral
Assault Boost = Tăng tốc tấn công
ACS Overload = Quá tải ACS
Pilot = Phi công
Mission Briefing = Thông tin nhiệm vụ
"""),
        listOf("returnal") to terms("""
Selene = Selene
Atropos = Atropos
Parasite = Ký sinh
Ether = Ether
Resin = Nhựa
Cycle = Vòng lặp
Biome = Khu sinh thái
"""),
        listOf("sifu") to terms("""
Pak Mei = Bạch Mi
Kung Fu = Kung Fu
Structure = Thế
Talisman = Bùa
Shrine = Miếu
Focus = Tập trung
Parry = Gạt đòn
"""),
        listOf("stellar blade") to terms("""
Naytiba = Naytiba
Xion = Xion
Beta Skill = Kỹ năng Beta
Burst Skill = Kỹ năng Bùng nổ
Tumbler = Tumbler
Camp = Trại
Nano Suit = Bộ đồ Nano
Exospine = Exospine
"""),
        listOf("wo long") to terms("""
Morale = Sĩ khí
Spirit = Linh khí
Wizardry Spell = Pháp thuật
Martial Art = Võ thuật
Battle Flag = Cờ chiến
Three Kingdoms = Tam Quốc
Deflect = Gạt đỡ
Fatal Strike = Đòn chí mạng
"""),
        listOf("octopath") to terms("""
Boost = Tăng lực
Break = Phá thế
Shield = Khiên
Path Action = Hành động Con đường
Latent Power = Sức mạnh Tiềm ẩn
Job = Nghề
Advanced Job = Nghề nâng cao
Leaves = Lá
"""),
        listOf("tales of arise") to terms("""
Boost Strike = Đòn Boost
Artes = Artes
Mystic Arte = Mystic Arte
Dahna = Dahna
Rena = Rena
Renan = Renan
Overlimit = Overlimit
Cure Point = Điểm hồi phục
"""),
        listOf("disco elysium") to terms("""
Thought Cabinet = Tủ Tư Duy
Inland Empire = Đế Quốc Nội Tâm
Volition = Ý chí
Empathy = Đồng cảm
Authority = Quyền uy
Shivers = Rùng mình
Encyclopedia = Bách khoa
Rhetoric = Hùng biện
Visual Calculus = Tính toán Thị giác
Perception = Quan sát
Composure = Bình tĩnh
Drama = Kịch tính
Conceptualization = Khái niệm hóa
Suggestion = Gợi ý
Esprit de Corps = Tinh thần đồng đội
Savoir Faire = Khéo léo
Hand/Eye Coordination = Phối hợp Tay-Mắt
Reaction Speed = Phản xạ
Electrochemistry = Điện hóa
Half Light = Nửa Ánh sáng
Pain Threshold = Ngưỡng Đau
Endurance = Sức bền
Physical Instrument = Thể lực
Interfacing = Kết nối
"""),
        listOf("celeste") to terms("""
Strawberry = Dâu
Dash = Lướt
Crystal Heart = Tim pha lê
B-Side = Mặt B
Madeline = Madeline
Badeline = Badeline
Summit = Đỉnh núi
"""),
        listOf("cuphead") to terms("""
Parry = Đỡ đòn
Super Art = Siêu chiêu
Coin = Xu
Contract = Hợp đồng
Inkwell Isle = Đảo Inkwell
Mugman = Mugman
"""),
        listOf("crash bandicoot") to terms("""
Wumpa Fruit = Quả Wumpa
Aku Aku = Aku Aku
Crate = Thùng
Gem = Ngọc
Relic = Di vật
Bounce Crate = Thùng nảy
TNT Crate = Thùng TNT
Nitro Crate = Thùng Nitro
Bonus Round = Màn thưởng
"""),
        listOf("ratchet") to terms("""
Rift = Khe nứt
Dimension = Chiều không gian
Bolts = Bu-lông
Raritanium = Raritanium
Hoverboots = Giày bay
Phantom Dash = Lướt Ảo ảnh
Omniwrench = Chìa khóa vạn năng
"""),
        listOf("sea of thieves") to terms("""
Pirate Legend = Huyền thoại Hải tặc
Gold Hoarders = Hội Tích Vàng
Merchant Alliance = Liên minh Thương nhân
Order of Souls = Hội Linh hồn
Sloop = Thuyền buồm nhỏ
Galleon = Thuyền galleon
Doubloons = Đồng doubloon
Kraken = Kraken
Megalodon = Megalodon
"""),
        listOf("dying light") to terms("""
Parkour = Parkour
Inhibitor = Chất ức chế
Infected = Người nhiễm
Volatile = Volatile
UV Light = Đèn UV
Peacekeepers = Lực lượng Gìn giữ
"""),
        listOf("days gone") to terms("""
Freaker = Freaker
Drifter = Kẻ lang bạt
Horde = Bầy
NERO = NERO
Ripper = Ripper
"""),
        listOf("metro") to terms("""
Aurora = Aurora
Gas Mask = Mặt nạ phòng độc
Filter = Bộ lọc
Military Grade Rounds = Đạn quân dụng
Nosalis = Nosalis
"""),
        listOf("call of duty") to terms("""
Killstreak = Chuỗi hạ gục
Loadout = Trang bị
Perk = Đặc quyền
Operator = Đặc nhiệm
Prestige = Danh hiệu
Spawn = Hồi sinh
Frag Grenade = Lựu đạn
UAV = UAV
"""),
        listOf("gears") to terms("""
COG = COG
Locust = Locust
Swarm = Swarm
Lancer = Lancer
Gnasher = Gnasher
Chainsaw = Cưa xích
Active Reload = Nạp đạn kích hoạt
"""),
        listOf("hellblade") to terms("""
Senua = Senua
Darkness = Bóng tối
Northmen = Người Phương Bắc
Furies = Furies
Focus = Tập trung
Hel = Hel
"""),
    )

    /** Thẻ nhân vật: (từ khoá tên game, các dòng "Tên = mô tả giọng, xưng hô; nhịp: ..."). */
    val CAST: List<Pair<List<String>, List<String>>> = listOf(
        listOf("witcher") to lines("""
Geralt = phù thủy săn quái vật; giọng trầm, khô, ít lời, hay mỉa mai nhẹ; xưng "tôi", gọi người đối diện "anh", "cô" hoặc "ngươi" tùy thái độ; nhịp: chậm
Yennefer = pháp sư; kiêu kỳ, sắc sảo, hay châm chọc; xưng "ta" hoặc "em" với Geralt; nhịp: vừa
Triss = pháp sư; ấm áp, thân thiện; xưng "em" với Geralt; nhịp: vừa
Ciri = cô gái trẻ; bộc trực, nhanh nhẹn; xưng "em" với Geralt; nhịp: nhanh
Dandelion/Jaskier = thi sĩ; lém lỉnh, hoa mỹ, nói nhiều; nhịp: nhanh
Vesemir = phù thủy già, như người cha; trầm ấm, gọi Geralt thân mật; xưng "ta"; nhịp: chậm
"""),
        listOf("god of war") to lines("""
Kratos = chiến binh Sparta; trầm, cộc lốc, nghiêm; xưng "ta", gọi Atreus là "con" hoặc "Atreus"; nhịp: chậm
Atreus = cậu bé; tò mò, hay hỏi, nói nhanh; xưng "con", gọi Kratos là "cha"; nhịp: nhanh
Mimir = cái đầu biết nói; lắm lời, dí dỏm, hay kể chuyện; gọi Kratos thân mật; nhịp: nhanh
Freya = nữ thần; trang nghiêm, đau thương, kiêu hãnh; xưng "ta"; nhịp: chậm
"""),
        listOf("cyberpunk") to lines("""
Johnny Silverhand = rocker nổi loạn; thô lỗ, hay chửi thề, mỉa mai; xưng "tao", gọi V là "nhóc" hoặc "mày"; nhịp: nhanh
Jackie Welles = bạn thân của V; nhiệt tình, bình dân, nói nhiều; gọi V thân mật như anh em; nhịp: nhanh
Judy Alvarez = kỹ thuật viên braindance; trầm tính, thẳng thắn; nhịp: vừa
Panam Palmer = nữ du mục; nóng tính, thẳng như ruột ngựa; nhịp: nhanh
"""),
        listOf("elden ring") to lines("""
Melina = người dẫn đường bí ẩn; dịu dàng, trang trọng; gọi người chơi là "ngài", xưng "tôi"; nhịp: chậm
Ranni = phù thủy, á thần; lạnh lùng, kiêu sa; xưng "ta"; nhịp: chậm
Blaidd = sói bán nhân; chân thành, thẳng thắn; xưng "tôi", gọi người chơi là "ngươi" hoặc "bạn"; nhịp: vừa
Patches = kẻ ranh ma, hay van xin; nhanh miệng, lươn lẹo nhưng hài hước; xưng "tôi"; nhịp: nhanh
"""),
        listOf("dark souls") to lines("""
Solaire = hiệp sĩ mặt trời; nhiệt thành, lạc quan, lịch thiệp; xưng "tôi"; nhịp: vừa
Siegmeyer = hiệp sĩ Catarina; hiền lành, ngây thơ, hay cười to; nhịp: vừa
"""),
        listOf("red dead") to lines("""
Arthur Morgan = tay súng miền Tây; trầm, cộc, mỉa mai khô; xưng "tôi"; nhịp: chậm
Dutch van der Linde = thủ lĩnh băng; hùng biện, quyến rũ, hay triết lý; xưng "tôi" hoặc "ta", gọi đồng bọn là "con trai"; nhịp: vừa
John Marston = ít nói, nghiêm, nóng ruột; nhịp: vừa
Micah Bell = xảo quyệt, châm chọc, hay khiêu khích; nhịp: nhanh
Sadie Adler = cứng rắn, thẳng, nóng nảy; nhịp: nhanh
"""),
        listOf("grand theft auto") to lines("""
Michael = trung niên cằn nhằn, mỉa mai, hay than thở; nhịp: vừa
Franklin = điềm tĩnh, nói chuyện đường phố, thực tế; nhịp: vừa
Trevor = điên loạn, chửi thề, nổi nóng thất thường; nhịp: nhanh
"""),
        listOf("last of us") to lines("""
Joel = người đàn ông chai sạn, cộc, hay giấu cảm xúc; xưng "tôi"; nhịp: chậm
Ellie = cô bé tinh nghịch, hay chửi thề, mỉa mai, nói nhanh; gọi Joel bằng tên; nhịp: nhanh
Abby = lạnh lùng, quyết đoán, ít nói; nhịp: vừa
Tess = cứng rắn, dứt khoát; nhịp: nhanh
"""),
        listOf("horizon") to lines("""
Aloy = thợ săn trẻ; thẳng thắn, tò mò, tự lập, hay mỉa mai nhẹ; xưng "tôi"; nhịp: vừa
Varl = chiến binh Nora; ấm áp, trung thành; nhịp: vừa
Erend = lính Carja; vui tính, to tiếng, hay đùa; nhịp: nhanh
Sylens = học giả lạnh lùng, tính toán, nói văn vẻ; nhịp: chậm
"""),
        listOf("spider-man") to lines("""
Peter Parker = Người Nhện; hay đùa ngay cả lúc nguy hiểm, lo cho người thân; nhịp: nhanh
Miles Morales = Người Nhện trẻ; nhiệt tình, hơi lóng ngóng, hay căng thẳng; nhịp: nhanh
Mary Jane Watson = nhà báo; thẳng thắn, can đảm; nhịp: vừa
"""),
        listOf("resident evil") to lines("""
Leon S. Kennedy = đặc vụ; điềm tĩnh, hay buông câu đùa khô; nhịp: vừa
Ada Wong = điệp viên bí ẩn; quyến rũ, lạnh, nói gọn; nhịp: chậm
Ashley = con gái tổng thống; hoảng hốt, hay gọi tên Leon; nhịp: nhanh
Lady Dimitrescu = quý bà; quý phái, lạnh lẽo, kiêu hãnh, xưng "ta"; nhịp: chậm
Ethan Winters = người đàn ông bình thường, lo cho gia đình, giọng căng thẳng; nhịp: nhanh
"""),
        listOf("zelda") to lines("""
Zelda = công chúa; dịu dàng, nghiêm túc, trang nhã; xưng "ta" hoặc "tôi"; nhịp: chậm
Ganondorf = chúa tể bóng tối; uy quyền, lạnh lẽo; xưng "ta"; nhịp: chậm
"""),
        listOf("persona") to lines("""
Ryuji = bạn của Joker; bộc trực, hơi thô, nói to; nhịp: nhanh
Morgana = con mèo biết nói; tự phụ, kiêu căng nhưng dễ thương; nhịp: nhanh
Ann = cô gái vui, thẳng, hay phản ứng mạnh; nhịp: nhanh
Futaba = cô gái mọt máy tính; nói nhanh, hay dùng tiếng lóng mạng; nhịp: nhanh
"""),
        listOf("final fantasy vii") to lines("""
Cloud = cựu lính đánh thuê; lạnh lùng, ít nói, hay ngập ngừng; nhịp: chậm
Tifa = dịu dàng, quan tâm, kiên định; nhịp: vừa
Aerith = vui vẻ, tinh nghịch, ấm áp; nhịp: vừa
Barret = nóng tính, to tiếng, thô nhưng tốt bụng; nhịp: nhanh
Sephiroth = lạnh lùng, kiêu ngạo, xưng "ta"; nhịp: chậm
"""),
        listOf("baldur's gate") to lines("""
Astarion = ma cà rồng; mỉa mai, kiêu căng, quyến rũ; nhịp: vừa
Karlach = chiến binh; nhiệt tình, to tiếng, thẳng thừng; nhịp: nhanh
Shadowheart = lạnh, đề phòng, nói khô; nhịp: chậm
Gale = pháp sư; hoa mỹ, học thức, hay nói dài; nhịp: vừa
Lae'zel = chiến binh githyanki; hống hách, ra lệnh, xưng "ta"; nhịp: nhanh
Wyll = hiệp sĩ; cao thượng, lịch thiệp; nhịp: vừa
"""),
        listOf("skyrim") to lines("""
Lydia = người hầu cận; nghiêm túc, trung thành, gọi người chơi là "Thane"; nhịp: vừa
Ulfric = thủ lĩnh Stormcloak; nghiêm nghị, uy quyền; nhịp: chậm
"""),
        listOf("fallout 4") to lines("""
Nick Valentine = thám tử robot; giọng khàn, mỉa mai nhẹ; nhịp: vừa
Piper = nhà báo; lanh lợi, hay mỉa; nhịp: nhanh
Codsworth = quản gia robot; lịch sự quá mức, gọi chủ là "phu nhân" hoặc "ngài"; nhịp: vừa
"""),
        listOf("nier") to lines("""
2B = android chiến đấu; lạnh lùng, ít cảm xúc, nói gọn; nhịp: chậm
9S = android trinh sát; tò mò, hào hứng, nói nhanh; nhịp: nhanh
A2 = android phản loạn; thô, cộc, cay nghiệt; nhịp: vừa
"""),
        listOf("death stranding") to lines("""
Sam = người giao hàng; trầm, ít lời, hay nói cộc; nhịp: chậm
Die-Hardman = trưởng bộ phận; nghiêm khắc, khô khan; nhịp: vừa
Higgs = kẻ phản diện; kịch tính, điên, khoa trương; nhịp: nhanh
"""),
        listOf("detroit") to lines("""
Connor = android điều tra; lịch sự, điềm tĩnh, nói chuẩn mực; nhịp: vừa
Kara = android giúp việc; dịu dàng, lo lắng, bảo vệ; nhịp: vừa
Markus = android lãnh đạo; trang trọng, truyền cảm; nhịp: chậm
Hank = thám tử già; thô ráp, mỉa mai, hay cằn nhằn; nhịp: vừa
"""),
        listOf("hades") to lines("""
Zagreus = hoàng tử Âm phủ; nhanh nhảu, mỉa mai, nói nhiều; nhịp: nhanh
Hades = chúa Âm phủ; uy nghiêm, lạnh, xưng "ta"; nhịp: chậm
Nyx = nữ thần Màn đêm; dịu dàng, trầm; nhịp: chậm
"""),
        listOf("uncharted") to lines("""
Nathan Drake = thợ săn kho báu; lém lỉnh, hay đùa, hay lẩm bẩm; nhịp: nhanh
Sully = bạn già; từng trải, hay châm chọc; nhịp: vừa
Elena = nhà báo; thẳng thắn, sắc sảo; nhịp: vừa
"""),
        listOf("kingdom hearts") to lines("""
Sora = cậu bé cầm Keyblade; hồn nhiên, nhiệt huyết; nhịp: nhanh
Donald = vịt pháp sư; nóng tính, giọng the thé; nhịp: nhanh
Goofy = hiệp sĩ; ngây ngô, hiền; nhịp: chậm
Riku = bạn của Sora; trầm, nghiêm; nhịp: vừa
"""),
        listOf("yakuza") to lines("""
Ichiban Kasuga = nhân vật chính; nhiệt huyết, ngây thơ, thật thà, nói bình dân; nhịp: nhanh
Nanba = cựu y tá vô gia cư; thực tế, hài hước, bình dân; nhịp: vừa
Saeko = chủ quán; sắc sảo, thẳng; nhịp: vừa
"""),
        listOf("alan wake") to lines("""
Saga Anderson = đặc vụ FBI; chuyên nghiệp, điềm tĩnh; nhịp: vừa
Alan Wake = nhà văn bị mắc kẹt; u uất, văn vẻ, ám ảnh; nhịp: chậm
"""),
        listOf("silent hill") to lines("""
James Sunderland = người đàn ông đi tìm vợ; u ám, rụt rè, ngập ngừng; nhịp: chậm
Maria = cô gái bí ẩn; quyến rũ, mơ hồ; nhịp: vừa
Angela = cô gái dè dặt, đau khổ, nói nhỏ; nhịp: chậm
"""),
        listOf("jedi") to lines("""
Cal Kestis = cựu Padawan; khiêm tốn, hay đùa nhẹ, dần chững chạc; nhịp: vừa
"""),
        listOf("control") to lines("""
Jesse Faden = giám đốc mới; bình tĩnh, tò mò; nhịp: vừa
"""),
    )

    private fun lines(s: String): List<String> = s.trim().lines().map { it.trim() }.filter { it.contains('=') }

    /** Dòng thông báo, tiêu đề, menu chung cho mọi game: câu gốc = câu Việt hoá (viết thường chữ đầu, app tự viết hoa khi câu gốc toàn chữ hoa). */
    val GENERIC_LINES: List<Pair<String, String>> = pairs("""
You died = Bạn đã chết
You are dead = Bạn đã chết
You're dead = Bạn đã chết
Game over = Thua cuộc
Mission complete = Hoàn thành nhiệm vụ
Mission completed = Hoàn thành nhiệm vụ
Mission passed = Hoàn thành nhiệm vụ
Mission failed = Nhiệm vụ thất bại
Quest complete = Hoàn thành nhiệm vụ
Quest completed = Hoàn thành nhiệm vụ
Quest cleared = Hoàn thành nhiệm vụ
Quest failed = Nhiệm vụ thất bại
New quest = Nhiệm vụ mới
Quest updated = Nhiệm vụ được cập nhật
Objective updated = Mục tiêu được cập nhật
New objective = Mục tiêu mới
Level up = Lên cấp
Checkpoint reached = Đã tới điểm lưu
Game saved = Đã lưu game
Saving = Đang lưu
Autosave = Tự động lưu
Loading = Đang tải
Paused = Tạm dừng
Press any button = Nhấn phím bất kỳ
Press any button to continue = Nhấn phím bất kỳ để tiếp tục
Press any key to continue = Nhấn phím bất kỳ để tiếp tục
Press start = Nhấn Start
New game = Chơi mới
Load game = Tải game
Continue = Tiếp tục
Options = Tùy chọn
Settings = Cài đặt
Quit game = Thoát game
Resume = Tiếp tục
Restart = Chơi lại
Retry = Thử lại
Restart from checkpoint = Chơi lại từ điểm lưu
Inventory = Túi đồ
Journal = Nhật ký
Skills = Kỹ năng
Equipment = Trang bị
Crafting = Chế tạo
Achievement unlocked = Đã mở khóa thành tựu
Trophy earned = Đã nhận cúp
New item acquired = Nhận vật phẩm mới
Item acquired = Nhận vật phẩm
Inventory full = Túi đồ đã đầy
Are you sure = Bạn có chắc không
Confirm = Xác nhận
Cancel = Hủy
Tutorial = Hướng dẫn
Victory = Chiến thắng
Defeat = Thất bại
You win = Bạn thắng
You lose = Bạn thua
Flawless victory = Chiến thắng hoàn hảo
Finish him = Kết liễu hắn
Finish her = Kết liễu cô ta
""")

    /** Thông báo đặc trưng của từng game hoặc series. */
    val SPECIFIC_LINES: List<Pair<List<String>, List<Pair<String, String>>>> = listOf(
        listOf("elden ring") to pairs("""
Great enemy felled = Đã hạ gục kẻ địch mạnh
Enemy felled = Đã hạ gục kẻ địch
Demigod felled = Đã hạ gục á thần
Legend felled = Đã hạ gục huyền thoại
Lost grace discovered = Đã tìm thấy điểm ban phước thất lạc
"""),
        listOf("dark souls") to pairs("""
Bonfire lit = Đã thắp lửa trại
Victory achieved = Chiến thắng
Humanity restored = Đã phục hồi nhân tính
Heir of fire destroyed = Kẻ kế thừa ngọn lửa đã bị tiêu diệt
"""),
        listOf("bloodborne") to pairs("""
Prey slaughtered = Con mồi đã bị tàn sát
Nightmare slain = Ác mộng đã bị diệt
"""),
        listOf("grand theft auto") to pairs("""
Wasted = Đã gục
Busted = Bị bắt
"""),
        listOf("assassin's creed") to pairs("""
Desynchronized = Mất đồng bộ
Full synchronization = Đồng bộ hoàn toàn
Memory desynchronized = Ký ức mất đồng bộ
"""),
        listOf("mortal kombat") to pairs("""
Fatality = Fatality
Test your might = Thử thách sức mạnh
"""),
    )

    fun castFor(name: String): List<String> {
        val n = name.lowercase()
        return CAST.filter { (keys, _) -> keys.any { n.contains(it) } }.flatMap { it.second }
    }

    fun specificLines(name: String): List<Pair<String, String>> {
        val n = name.lowercase()
        return SPECIFIC_LINES.filter { (keys, _) -> keys.any { n.contains(it) } }.flatMap { it.second }
    }
}
