package com.dichgame.app

/** Kho game: danh sách game phổ biến để chọn nhanh: (tên game, thể loại), kèm nền tảng và gói thuật ngữ Việt hoá (xem VietHoa.kt). Thể loại được đưa cho bộ dịch AI để dịch đúng giọng. */
object Catalog {
    val GAMES: List<Pair<String, String>> = listOf(
        "Alan Wake 2" to "kinh dị",
        "Armored Core VI: Fires of Rubicon" to "hành động robot",
        "Assassin's Creed Mirage" to "hành động phiêu lưu lịch sử",
        "Assassin's Creed Shadows" to "nhập vai hành động Nhật Bản phong kiến",
        "Assassin's Creed Valhalla" to "nhập vai hành động Viking",
        "Astro Bot" to "phiêu lưu platform",
        "Atomic Heart" to "bắn súng viễn tưởng",
        "Baldur's Gate 3" to "nhập vai giả tưởng",
        "Batman: Arkham Knight" to "hành động siêu anh hùng",
        "Black Myth: Wukong" to "hành động thần thoại Trung Hoa",
        "Bloodborne" to "hành động nhập vai hắc ám",
        "Call of Duty: Modern Warfare III" to "bắn súng quân sự",
        "Celeste" to "platform",
        "Control" to "hành động siêu nhiên",
        "Crash Bandicoot N. Sane Trilogy" to "platform",
        "Cuphead" to "platform bắn súng",
        "Cyberpunk 2077" to "nhập vai viễn tưởng",
        "Dark Souls III" to "hành động nhập vai hắc ám",
        "Days Gone" to "sinh tồn zombie",
        "Dead Space" to "kinh dị viễn tưởng",
        "Death Stranding" to "hành động kể chuyện",
        "Detroit: Become Human" to "kể chuyện tương tác",
        "Devil May Cry 5" to "hành động",
        "Diablo IV" to "nhập vai hành động hắc ám",
        "Disco Elysium" to "nhập vai trinh thám",
        "Dragon Age: The Veilguard" to "nhập vai giả tưởng",
        "Dragon Ball Z: Kakarot" to "nhập vai hành động",
        "Dragon Quest XI S" to "nhập vai Nhật Bản",
        "Dragon's Dogma 2" to "nhập vai giả tưởng",
        "Dying Light 2" to "sinh tồn zombie",
        "EA Sports FC 25" to "bóng đá",
        "Elden Ring" to "nhập vai hành động giả tưởng",
        "Fallout 4" to "nhập vai hậu tận thế",
        "Far Cry 6" to "bắn súng thế giới mở",
        "Final Fantasy VII Remake" to "nhập vai Nhật Bản",
        "Final Fantasy VII Rebirth" to "nhập vai Nhật Bản",
        "Final Fantasy XVI" to "nhập vai hành động giả tưởng",
        "Forza Horizon 5" to "đua xe",
        "Gears 5" to "bắn súng",
        "Ghost of Tsushima" to "hành động Nhật Bản phong kiến",
        "Ghostwire: Tokyo" to "hành động siêu nhiên",
        "God of War" to "hành động thần thoại Bắc Âu",
        "God of War Ragnarök" to "hành động thần thoại Bắc Âu",
        "Gran Turismo 7" to "đua xe",
        "Grand Theft Auto V" to "hành động thế giới mở",
        "Hades" to "hành động roguelike thần thoại Hy Lạp",
        "Halo Infinite" to "bắn súng viễn tưởng",
        "Hellblade: Senua's Sacrifice" to "hành động kể chuyện",
        "Hi-Fi Rush" to "hành động nhịp điệu",
        "Hogwarts Legacy" to "nhập vai phép thuật",
        "Hollow Knight" to "metroidvania",
        "Horizon Forbidden West" to "hành động thế giới mở viễn tưởng",
        "Horizon Zero Dawn" to "hành động thế giới mở viễn tưởng",
        "It Takes Two" to "hợp tác phiêu lưu",
        "Kingdom Come: Deliverance" to "nhập vai lịch sử",
        "Kingdom Hearts III" to "nhập vai hành động",
        "Lies of P" to "hành động nhập vai hắc ám",
        "Little Nightmares II" to "kinh dị giải đố",
        "Lords of the Fallen" to "nhập vai hành động hắc ám",
        "Marvel's Guardians of the Galaxy" to "hành động viễn tưởng",
        "Marvel's Spider-Man 2" to "hành động siêu anh hùng",
        "Marvel's Spider-Man: Miles Morales" to "hành động siêu anh hùng",
        "Mass Effect Legendary Edition" to "nhập vai viễn tưởng",
        "Metal Gear Solid Delta: Snake Eater" to "hành động lén lút",
        "Metro Exodus" to "bắn súng hậu tận thế",
        "Monster Hunter Wilds" to "săn quái vật",
        "Monster Hunter: World" to "săn quái vật",
        "Mortal Kombat 1" to "đối kháng",
        "NBA 2K25" to "bóng rổ",
        "Nier: Automata" to "nhập vai hành động viễn tưởng",
        "Nioh 2" to "hành động nhập vai Nhật Bản",
        "Octopath Traveler II" to "nhập vai Nhật Bản",
        "Outlast" to "kinh dị",
        "Persona 3 Reload" to "nhập vai Nhật Bản",
        "Persona 5 Royal" to "nhập vai Nhật Bản",
        "Pokémon Scarlet & Violet" to "nhập vai",
        "Prince of Persia: The Lost Crown" to "metroidvania",
        "Ratchet & Clank: Rift Apart" to "hành động platform",
        "Red Dead Redemption 2" to "hành động miền Tây thế giới mở",
        "Remnant II" to "bắn súng nhập vai",
        "Resident Evil 2" to "kinh dị",
        "Resident Evil 4" to "kinh dị hành động",
        "Resident Evil Village" to "kinh dị",
        "Returnal" to "bắn súng roguelike",
        "Sea of Thieves" to "phiêu lưu hải tặc",
        "Sekiro: Shadows Die Twice" to "hành động Nhật Bản phong kiến",
        "Sifu" to "đối kháng kung fu",
        "Silent Hill 2" to "kinh dị tâm lý",
        "Star Wars Jedi: Survivor" to "hành động Star Wars",
        "Star Wars Outlaws" to "hành động thế giới mở Star Wars",
        "Starfield" to "nhập vai không gian",
        "Stardew Valley" to "mô phỏng nông trại",
        "Stellar Blade" to "hành động viễn tưởng",
        "Stray" to "phiêu lưu",
        "Street Fighter 6" to "đối kháng",
        "Super Mario Odyssey" to "platform",
        "Tales of Arise" to "nhập vai Nhật Bản",
        "Tekken 8" to "đối kháng",
        "The Callisto Protocol" to "kinh dị viễn tưởng",
        "The Elder Scrolls V: Skyrim" to "nhập vai giả tưởng",
        "The Last of Us Part I" to "sinh tồn hậu tận thế",
        "The Last of Us Part II" to "sinh tồn hậu tận thế",
        "The Legend of Zelda: Breath of the Wild" to "phiêu lưu thế giới mở giả tưởng",
        "The Legend of Zelda: Tears of the Kingdom" to "phiêu lưu thế giới mở giả tưởng",
        "The Outer Worlds" to "nhập vai viễn tưởng",
        "The Witcher 3: Wild Hunt" to "nhập vai giả tưởng",
        "Uncharted 4: A Thief's End" to "hành động phiêu lưu",
        "Until Dawn" to "kinh dị kể chuyện",
        "Wo Long: Fallen Dynasty" to "hành động nhập vai Tam Quốc",
        "Yakuza: Like a Dragon" to "nhập vai xã hội đen Nhật Bản",
    ).sortedBy { it.first.lowercase() }

    fun genreOf(name: String): String = GAMES.firstOrNull { it.first == name }?.second.orEmpty()

    /** Chữ viết tắt hiện trên ô game. Tên dài thì lấy hai từ đầu và từ cuối để các phần của cùng một series không trùng nhau. */
    fun initials(name: String): String {
        val skip = setOf("the", "of", "a", "and", "&")
        val words = name.split(Regex("[^\\p{L}\\p{N}]+")).filter { it.isNotEmpty() && it.lowercase() !in skip }
        val pick = if (words.size > 3) listOf(words[0], words[1], words.last()) else words
        val s = pick.joinToString("") { it.take(1) }.uppercase()
        return s.ifEmpty { name.take(2).uppercase() }
    }

    /** Sắc màu 0..359 của ô game, cố định theo tên. */
    fun hue(name: String): Int = ((name.hashCode() % 360) + 360) % 360

    /**
     * Nền tảng của từng game, mỗi chữ là một nền tảng: 5 = PS5, 4 = PS4, X = Xbox, P = PC, S = có trên Steam, N = Switch.
     * Chỉ để tham khảo, có thể đã thay đổi. Game PS4 chạy được trên PS5, game Xbox One chạy được trên Xbox Series nhờ tương thích ngược.
     */
    private val PLATFORM_CODES: Map<String, String> = mapOf(
        "Alan Wake 2" to "5XP",
        "Armored Core VI: Fires of Rubicon" to "45XPS",
        "Assassin's Creed Mirage" to "45XP",
        "Assassin's Creed Shadows" to "5XPS",
        "Assassin's Creed Valhalla" to "45XP",
        "Astro Bot" to "5",
        "Atomic Heart" to "45XPS",
        "Baldur's Gate 3" to "5XPS",
        "Batman: Arkham Knight" to "45XPS",
        "Black Myth: Wukong" to "5XPS",
        "Bloodborne" to "4",
        "Call of Duty: Modern Warfare III" to "45XP",
        "Celeste" to "45XPSN",
        "Control" to "45XPS",
        "Crash Bandicoot N. Sane Trilogy" to "45XPSN",
        "Cuphead" to "45XPSN",
        "Cyberpunk 2077" to "45XPS",
        "Dark Souls III" to "45XPS",
        "Days Gone" to "45PS",
        "Dead Space" to "5XPS",
        "Death Stranding" to "45PS",
        "Detroit: Become Human" to "4PS",
        "Devil May Cry 5" to "45XPS",
        "Diablo IV" to "45XPS",
        "Disco Elysium" to "45XPSN",
        "Dragon Age: The Veilguard" to "5XPS",
        "Dragon Ball Z: Kakarot" to "45XPSN",
        "Dragon Quest XI S" to "45XPSN",
        "Dragon's Dogma 2" to "5XPS",
        "Dying Light 2" to "45XPSN",
        "EA Sports FC 25" to "45XPSN",
        "Elden Ring" to "45XPS",
        "Fallout 4" to "45XPS",
        "Far Cry 6" to "45XP",
        "Final Fantasy VII Rebirth" to "5PS",
        "Final Fantasy VII Remake" to "45PS",
        "Final Fantasy XVI" to "5PS",
        "Forza Horizon 5" to "5XPS",
        "Gears 5" to "XPS",
        "Ghost of Tsushima" to "45PS",
        "Ghostwire: Tokyo" to "5XPS",
        "God of War" to "45PS",
        "God of War Ragnarök" to "45PS",
        "Gran Turismo 7" to "45",
        "Grand Theft Auto V" to "45XPS",
        "Hades" to "45XPSN",
        "Halo Infinite" to "XPS",
        "Hellblade: Senua's Sacrifice" to "45XPS",
        "Hi-Fi Rush" to "5XPS",
        "Hogwarts Legacy" to "45XPSN",
        "Hollow Knight" to "45XPSN",
        "Horizon Forbidden West" to "45PS",
        "Horizon Zero Dawn" to "45PS",
        "It Takes Two" to "45XPSN",
        "Kingdom Come: Deliverance" to "4XPS",
        "Kingdom Hearts III" to "4XPS",
        "Lies of P" to "45XPS",
        "Little Nightmares II" to "45XPSN",
        "Lords of the Fallen" to "5XPS",
        "Marvel's Guardians of the Galaxy" to "45XPSN",
        "Marvel's Spider-Man 2" to "5PS",
        "Marvel's Spider-Man: Miles Morales" to "45PS",
        "Mass Effect Legendary Edition" to "45XPS",
        "Metal Gear Solid Delta: Snake Eater" to "5XPS",
        "Metro Exodus" to "45XPS",
        "Monster Hunter Wilds" to "5XPS",
        "Monster Hunter: World" to "45XPS",
        "Mortal Kombat 1" to "5XPSN",
        "NBA 2K25" to "45XPSN",
        "Nier: Automata" to "45XPSN",
        "Nioh 2" to "45PS",
        "Octopath Traveler II" to "45PSN",
        "Outlast" to "45XPSN",
        "Persona 3 Reload" to "45XPS",
        "Persona 5 Royal" to "45XPSN",
        "Pokémon Scarlet & Violet" to "N",
        "Prince of Persia: The Lost Crown" to "45XPSN",
        "Ratchet & Clank: Rift Apart" to "5PS",
        "Red Dead Redemption 2" to "45XPS",
        "Remnant II" to "5XPS",
        "Resident Evil 2" to "45XPS",
        "Resident Evil 4" to "45XPS",
        "Resident Evil Village" to "45XPS",
        "Returnal" to "5PS",
        "Sea of Thieves" to "5XPS",
        "Sekiro: Shadows Die Twice" to "45XPS",
        "Sifu" to "45XPSN",
        "Silent Hill 2" to "5PS",
        "Star Wars Jedi: Survivor" to "5XPS",
        "Star Wars Outlaws" to "5XP",
        "Stardew Valley" to "45XPSN",
        "Starfield" to "XPS",
        "Stellar Blade" to "5PS",
        "Stray" to "45XPSN",
        "Street Fighter 6" to "45XPS",
        "Super Mario Odyssey" to "N",
        "Tales of Arise" to "45XPS",
        "Tekken 8" to "5XPS",
        "The Callisto Protocol" to "45XPS",
        "The Elder Scrolls V: Skyrim" to "45XPSN",
        "The Last of Us Part I" to "5PS",
        "The Last of Us Part II" to "45PS",
        "The Legend of Zelda: Breath of the Wild" to "N",
        "The Legend of Zelda: Tears of the Kingdom" to "N",
        "The Outer Worlds" to "45XPSN",
        "The Witcher 3: Wild Hunt" to "45XPSN",
        "Uncharted 4: A Thief's End" to "45PS",
        "Until Dawn" to "45PS",
        "Wo Long: Fallen Dynasty" to "45XPS",
        "Yakuza: Like a Dragon" to "45XPS",
    )

    /** Các nền tảng ghi trong danh sách (rỗng nếu game tự thêm, chưa có thông tin). */
    fun platformsOf(name: String): Set<Plat> {
        val c = PLATFORM_CODES[name] ?: return emptySet()
        val out = LinkedHashSet<Plat>()
        if ('5' in c) out.add(Plat.PS5)
        if ('4' in c) out.add(Plat.PS4)
        if ('X' in c) out.add(Plat.XBOX)
        if ('P' in c || 'S' in c) out.add(Plat.PC)
        if ('S' in c) out.add(Plat.STEAM)
        if ('N' in c) out.add(Plat.SWITCH)
        return out
    }

    /** Chạy được trên nền tảng này không (PS5 tính cả game PS4 nhờ tương thích ngược). Game chưa có thông tin thì coi là có. */
    fun runsOn(name: String, p: Plat): Boolean {
        val c = PLATFORM_CODES[name] ?: return true
        return when (p) {
            Plat.PS5 -> '5' in c || '4' in c
            Plat.PS4 -> '4' in c
            Plat.XBOX -> 'X' in c
            Plat.PC -> 'P' in c || 'S' in c
            Plat.STEAM -> 'S' in c
            Plat.SWITCH -> 'N' in c
        }
    }
}

/** Nền tảng chơi game. */
enum class Plat(val label: String) { PS5("PS5"), PS4("PS4"), XBOX("Xbox"), PC("PC"), STEAM("Steam"), SWITCH("Switch") }
