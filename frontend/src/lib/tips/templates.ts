import type { TipTemplate } from './types';

/**
 * 待ち時間中に右パネルへ表示する Tip テンプレート集。
 * 文体: 優しく語りかけるような口調 (〜ですよ / 〜してみてください / 〜と言われています)
 * 本文長: 60〜140 文字目安
 */
export const TIP_TEMPLATES: TipTemplate[] = [
  // ===== 雪質・ゲレンデコンディション =====
  {
    id: 'snow-001',
    category: 'SNOW',
    icon: '❄️',
    title: '雪質コンディション',
    message:
      '{{resort}}は今シーズン平均 {{snowDepth}}cm 前後の積雪と言われています。週末はパウダー狙いの方も多いみたいですよ。',
    months: [12, 1, 2, 3],
  },
  {
    id: 'snow-002',
    category: 'SNOW',
    icon: '🌨️',
    title: '朝の雪質',
    message:
      '冷え込んだ朝の最初の 1 本は、圧雪バーンが整って気持ちよく滑れるそうですよ。少し早起きしてみるのもおすすめです。',
  },
  {
    id: 'snow-003',
    category: 'SNOW',
    icon: '☃️',
    title: '午後のコンディション',
    message:
      '日が高くなる午後は、南斜面が緩んで足を取られやすくなることがあります。北向き斜面で気持ちよく滑り続けるのも一つの楽しみ方ですね。',
    months: [3, 4],
  },
  {
    id: 'snow-004',
    category: 'SNOW',
    icon: '🌬️',
    title: 'パウダー狙いの心得',
    message:
      'パウダーを楽しむなら、降雪翌日の朝一番が狙い目だそうです。早起きが少し大変ですが、その価値はあるかもしれませんね。',
    months: [12, 1, 2],
    skillLevels: ['INTERMEDIATE', 'ADVANCED', 'EXPERT'],
  },
  {
    id: 'snow-005',
    category: 'SNOW',
    icon: '💎',
    title: 'アイスバーン対策',
    message:
      '気温が低い朝はアイスバーンになりやすいそうですよ。エッジを立てた丁寧な滑りを意識すると安心して降りられます。',
    months: [1, 2],
    skillLevels: ['BEGINNER', 'INTERMEDIATE'],
  },

  // ===== リフト運行・コース開放 =====
  {
    id: 'lift-001',
    category: 'LIFT',
    icon: '🚡',
    title: 'リフトの混雑時間',
    message:
      '朝一番のメインリフトは 9:00 過ぎに混み始めることが多いそうです。早めに動くと待ち時間がぐっと短くなりますよ。',
  },
  {
    id: 'lift-002',
    category: 'LIFT',
    icon: '🏔️',
    title: '山頂リフトの注意',
    message:
      '風の強い日は山頂リフトが運休になることもあるそうです。当日朝の運行情報を SNS などで確認しておくと安心ですね。',
  },
  {
    id: 'lift-003',
    category: 'LIFT',
    icon: '🎟️',
    title: 'リフト券のお得情報',
    message:
      '前日までの web 購入で 1,000 円ほどお得になるリフト券もあるそうですよ。準備のついでにチェックしてみてくださいね。',
  },
  {
    id: 'lift-004',
    category: 'LIFT',
    icon: '🚠',
    title: 'ロングコース活用',
    message:
      '{{resort}} には長距離を一気に滑れるコースがあるそうです。リフトの待ち時間を考えると、トータルの満足度が高いみたいですよ。',
    resorts: ['苗場', 'かぐら', '志賀高原', '白馬'],
  },

  // ===== アクセス・積雪路面 =====
  {
    id: 'access-001',
    category: 'ACCESS',
    icon: '🚗',
    title: 'アクセス情報',
    message:
      '{{resort}} は {{accessIc}} から約 {{km}}km。冬期はチェーン規制が出る区間もあるそうなので、冬タイヤ＋チェーンの携行が安心です。',
  },
  {
    id: 'access-002',
    category: 'ACCESS',
    icon: '🚌',
    title: 'シャトルバス',
    message:
      '駅から無料シャトルバスが出ている宿も多いそうですよ。事前に到着時刻を伝えておくと、スムーズに送迎してもらえることが多いみたいです。',
  },
  {
    id: 'access-003',
    category: 'ACCESS',
    icon: '🚆',
    title: '電車でのアクセス',
    message:
      '荷物が多いときは、宅急便で板やブーツを先に宿へ送っておく方法もあるそうです。手ぶらで電車に乗れて快適に到着できますね。',
  },
  {
    id: 'access-004',
    category: 'ACCESS',
    icon: '⛽',
    title: '雪道ドライブの心得',
    message:
      '雪道では普段の倍くらいの車間距離を取ると安心だそうです。下り坂はエンジンブレーキを使って、フットブレーキは控えめにしてみてください。',
    skillLevels: ['BEGINNER'],
  },

  // ===== レンタル・チューンナップ =====
  {
    id: 'rental-001',
    category: 'RENTAL',
    icon: '🛠️',
    title: 'レンタル選びのコツ',
    message:
      'レンタル板は身長 -10cm が初心者の目安と言われています。中級以上の方は身長と同じくらいだと操作性と安定性のバランスがよいそうですよ。',
    skillLevels: ['BEGINNER', 'INTERMEDIATE'],
  },
  {
    id: 'rental-002',
    category: 'RENTAL',
    icon: '🥾',
    title: 'ブーツの事前予約',
    message:
      'レンタルブーツはサイズによっては当日完売することもあるそうです。事前予約しておくと、当日スムーズに滑り始められますね。',
  },
  {
    id: 'rental-003',
    category: 'RENTAL',
    icon: '✨',
    title: 'チューンナップ',
    message:
      'シーズン初めにエッジ研磨とワックスをかけておくと、走りが見違えるそうですよ。少し手間ですが、滑りの質が変わると言われています。',
    months: [11, 12],
    skillLevels: ['INTERMEDIATE', 'ADVANCED', 'EXPERT'],
  },
  {
    id: 'rental-004',
    category: 'RENTAL',
    icon: '🎿',
    title: '試乗会のおすすめ',
    message:
      'メーカーの試乗会では最新モデルを無料で試せることが多いそうです。購入前の情報収集にぴったりですね。',
    months: [12, 1, 2, 3],
    skillLevels: ['INTERMEDIATE', 'ADVANCED', 'EXPERT'],
  },

  // ===== スクール・レッスン =====
  {
    id: 'school-001',
    category: 'SCHOOL',
    icon: '🎓',
    title: 'スクールの選び方',
    message:
      'プライベートレッスンは上達が早いと評判だそうですよ。週末は予約が埋まりやすいので、早めの申し込みが安心です。',
  },
  {
    id: 'school-002',
    category: 'SCHOOL',
    icon: '👶',
    title: 'キッズスクール',
    message:
      '4 歳から参加できるキッズスクールがあるスキー場も多いそうです。家族で別行動の時間が作れるので、大人もゆっくり楽しめますね。',
  },
  {
    id: 'school-003',
    category: 'SCHOOL',
    icon: '📈',
    title: '上達のヒント',
    message:
      '同じレベルを 1 日かけて練習するより、半日レッスンで基礎を整えてから自由滑走する方が、上達が早いと言われていますよ。',
    skillLevels: ['BEGINNER', 'INTERMEDIATE'],
  },

  // ===== 宿泊（乾燥室・温泉） =====
  {
    id: 'lodging-001',
    category: 'LODGING',
    icon: '🏨',
    title: '乾燥室は重要',
    message:
      '乾燥室付きの宿だと、翌朝ブーツがほんのり温かくて快適だそうですよ。予約サイトで「乾燥室」で絞り込んでみてくださいね。',
  },
  {
    id: 'lodging-002',
    category: 'LODGING',
    icon: '♨️',
    title: '温泉でリラックス',
    message:
      '{{resort}} の麓には {{nearestOnsen}} がありますね。滑り終えた後の温泉は最高のご褒美になりますよ。',
    resorts: ['苗場', 'かぐら', '湯沢', '妙高', '赤倉', '白馬', '志賀高原', '野沢温泉', 'ニセコ', 'ルスツ'],
  },
  {
    id: 'lodging-003',
    category: 'LODGING',
    icon: '🚐',
    title: '送迎付きの宿',
    message:
      'スキー場まで送迎してくれる宿だと、駐車場探しの手間がなくて朝もゆっくりできるそうです。当日の体力温存にもなりますね。',
  },
  {
    id: 'lodging-004',
    category: 'LODGING',
    icon: '🛁',
    title: '深夜チェックイン',
    message:
      '夜遅く到着するときは、深夜チェックインに対応している宿を選ぶと安心ですね。事前連絡を入れておくとスムーズだそうですよ。',
  },

  // ===== ゲレンデ内飲食 =====
  {
    id: 'dining-001',
    category: 'DINING',
    icon: '🍜',
    title: 'お昼の混雑回避',
    message:
      'ゲレンデ内のレストランは 12:30 を過ぎると混みやすいそうです。11:30 か 13:30 をねらうと、ゆっくり座って食事できますよ。',
  },
  {
    id: 'dining-002',
    category: 'DINING',
    icon: '☕',
    title: 'カフェでひと休み',
    message:
      '滑り疲れたら、ゲレンデ脇のカフェでホットドリンクを楽しむのもいいですね。足の裏のじんわりした疲れがほぐれていきますよ。',
  },
  {
    id: 'dining-003',
    category: 'DINING',
    icon: '🍲',
    title: '地元グルメ',
    message:
      '{{resort}} 周辺は地元の郷土料理が美味しいことで知られているそうです。夜の食事も楽しみのひとつになりますね。',
  },

  // ===== 安全・パトロール =====
  {
    id: 'safety-001',
    category: 'SAFETY',
    icon: '⛑️',
    title: 'ヘルメットのすすめ',
    message:
      '近年はヘルメット着用が一般的になってきたそうです。万一の転倒時にも頭部を守れるので、レンタルでも気軽に試してみてください。',
  },
  {
    id: 'safety-002',
    category: 'SAFETY',
    icon: '🚨',
    title: 'コース外への注意',
    message:
      'コース外は雪崩のリスクがある区域もあるそうです。視界の悪い日は無理せず、整備されたコースを楽しむのが安心ですね。',
    skillLevels: ['BEGINNER', 'INTERMEDIATE'],
  },
  {
    id: 'safety-003',
    category: 'SAFETY',
    icon: '👀',
    title: '視界の確保',
    message:
      '吹雪の日はゴーグルの曇り止めスプレーが大活躍するそうですよ。視界がクリアだと安全性も滑りの楽しさも段違いです。',
  },
  {
    id: 'safety-004',
    category: 'SAFETY',
    icon: '🆘',
    title: '万一の備え',
    message:
      '万一ケガをしたら、近くのリフト係員かパトロールに声をかけてくださいね。スキー場には救護所があるので、すぐに対応してもらえるそうです。',
  },

  // ===== ギア・装備 =====
  {
    id: 'gear-warmup',
    category: 'GEAR',
    icon: '🧥',
    title: 'レイヤリングのコツ',
    message:
      '気温 -10℃ を下回る朝は、ベース＋ミドル＋シェルの 3 層構成が安心だそうです。汗冷えを防ぐ吸湿速乾のベースレイヤーが効果的ですよ。',
    months: [12, 1, 2],
    skillLevels: ['BEGINNER', 'INTERMEDIATE'],
  },
  {
    id: 'gear-002',
    category: 'GEAR',
    icon: '🧤',
    title: 'グローブの選び方',
    message:
      '指先が冷えやすい方は、ミトン型のグローブも選択肢に入れてみてください。指同士の体温で温かさが増すと言われていますよ。',
  },
  {
    id: 'gear-003',
    category: 'GEAR',
    icon: '🪶',
    title: 'ワックスの目安',
    message:
      'その日の雪温に合わせたワックスを薄く塗ると、滑走性が変わるそうですよ。冷え込む朝は低温用、春の湿雪には高温用が向いていると言われています。',
  },
  {
    id: 'gear-004',
    category: 'GEAR',
    icon: '🥽',
    title: 'ゴーグルのレンズ選び',
    message:
      '曇りや雪の日は黄色〜オレンジ系の明るいレンズだと視界が確保しやすいそうです。シーンに合わせて使い分けると快適ですよ。',
  },
  {
    id: 'gear-005',
    category: 'GEAR',
    icon: '💸',
    title: 'レンタル vs 購入',
    message:
      'シーズンに 5 日以上滑るなら購入の方が長期的にはお得だと言われています。3 日以下ならレンタルで気軽に楽しむのもいいですね。',
    skillLevels: ['INTERMEDIATE', 'ADVANCED', 'EXPERT'],
  },
  {
    id: 'gear-006',
    category: 'GEAR',
    icon: '🌡️',
    title: '寒波の日の装備',
    message:
      '寒波接近時は、フェイスマスクとハンドウォーマーがあると安心です。とくに首元と指先の保温で、体感温度がぐっと変わりますよ。',
  },

  // ===== イベント・ナイター =====
  {
    id: 'event-001',
    category: 'EVENT',
    icon: '🌙',
    title: 'ナイター営業',
    message:
      'ナイター営業のあるスキー場では、夜の幻想的なゲレンデを楽しめるそうですよ。リフト券もお手頃なところが多いみたいです。',
    resorts: ['苗場', '湯沢', '赤倉', '白馬', 'ニセコ', 'ルスツ'],
  },
  {
    id: 'event-002',
    category: 'EVENT',
    icon: '🏆',
    title: '大会・イベント',
    message:
      'シーズン中は大会やフェスが各地で開催されるそうです。観戦も楽しいですし、見ることで自分の滑りのヒントが得られるかもしれませんね。',
    months: [1, 2, 3],
  },
  {
    id: 'event-003',
    category: 'EVENT',
    icon: '🎆',
    title: '花火イベント',
    message:
      '冬の夜空に上がる花火がゲレンデから見られるイベントを開催しているスキー場もあるそうです。素敵な思い出になりそうですね。',
    months: [12, 1, 2],
  },

  // ============================================================
  // 以下、キュレーション済みリゾート専用テンプレ
  // requires が満たされない resort では非表示。
  // priority を高めに設定して「現地情報感」を全面に出す。
  // ============================================================

  // ----- リゾート紹介 (signature) -----
  {
    id: 'curated-signature-001',
    category: 'EVENT',
    icon: '✨',
    title: '{{resort}}のここが特別',
    message: '{{signature}}',
    requires: ['signature'],
    priority: 4,
  },

  // ----- ゲレ食・周辺グルメ -----
  {
    id: 'curated-dining-001',
    category: 'DINING',
    icon: '🍽️',
    title: 'おすすめのお店「{{restaurant.name}}」',
    message:
      '{{restaurant.location}}にある「{{restaurant.name}}」は{{restaurant.dish}}が人気だそうです。{{restaurant.viewPoint}}',
    requires: ['recommendedRestaurants'],
    priority: 5,
  },
  {
    id: 'curated-dining-002',
    category: 'DINING',
    icon: '🍴',
    title: 'お昼の予算メモ',
    message:
      '{{resort}}の人気店「{{restaurant.name}}」では、{{restaurant.dish}}が{{restaurant.price}}くらいのよくある価格帯と言われています。',
    requires: ['recommendedRestaurants'],
    priority: 4,
  },
  {
    id: 'curated-dining-003',
    category: 'DINING',
    icon: '☕',
    title: 'カフェタイムにも',
    message:
      '「{{restaurant.name}}」({{restaurant.location}})は{{restaurant.note}}という声があるそうです。',
    requires: ['recommendedRestaurants'],
    priority: 3,
  },

  // ----- 温泉 -----
  {
    id: 'curated-onsen-001',
    category: 'LODGING',
    icon: '♨️',
    title: '滑った後は「{{onsen.name}}」へ',
    message:
      '{{onsen.name}}は{{onsen.spring}}で、入湯料は{{onsen.price}}くらいだそうです。{{onsen.note}}',
    requires: ['nearbyOnsen'],
    priority: 5,
  },
  {
    id: 'curated-onsen-002',
    category: 'LODGING',
    icon: '🛁',
    title: '近場の温泉メモ',
    message:
      '{{resort}}から約 {{onsen.distance}}km にある「{{onsen.name}}」は、{{onsen.spring}}が楽しめるそうですよ。',
    requires: ['nearbyOnsen'],
    priority: 4,
  },
  {
    id: 'curated-onsen-003',
    category: 'LODGING',
    icon: '♨️',
    title: '泉質メモ',
    message:
      '{{onsen.name}}の泉質は「{{onsen.spring}}」と言われています。冷えた身体がじんわり温まりそうですね。',
    requires: ['nearbyOnsen'],
    priority: 3,
  },

  // ----- 安全 -----
  {
    id: 'curated-safety-001',
    category: 'SAFETY',
    icon: '⚠️',
    title: '{{safety.area}}でのご注意',
    message: '{{safety.area}}は{{safety.hazard}}と言われています。{{safety.advice}}。',
    requires: ['safetyNotes'],
    priority: 4,
  },
  {
    id: 'curated-safety-002',
    category: 'SAFETY',
    icon: '🚨',
    title: '現地パトロールからの一言',
    message:
      '{{resort}}では、{{safety.advice}}と案内されているそうです。安全に楽しんでくださいね。',
    requires: ['safetyNotes'],
    priority: 3,
  },

  // ----- キッズ施設 -----
  {
    id: 'curated-kids-001',
    category: 'SCHOOL',
    icon: '👶',
    title: 'キッズ向けに「{{kids.name}}」',
    message:
      '{{kids.ageMin}}歳から参加できる「{{kids.name}}」があるみたいです。料金は{{kids.price}}くらいだそうですよ。',
    requires: ['kidsFacility'],
    priority: 4,
  },
  {
    id: 'curated-kids-002',
    category: 'SCHOOL',
    icon: '🧒',
    title: 'お子様連れの方へ',
    message:
      '{{resort}}には「{{kids.name}}」があり、{{kids.note}}と言われています。',
    requires: ['kidsFacility'],
    priority: 3,
  },

  // ----- コース構成 -----
  {
    id: 'curated-course-001',
    category: 'LIFT',
    icon: '🎿',
    title: '最長コースは {{course.longestRun}}m',
    message:
      '{{resort}}の最長コースは約 {{course.longestRun}}m あると言われています。{{course.note}}',
    requires: ['courses'],
    priority: 4,
  },
  {
    id: 'curated-course-002',
    category: 'LIFT',
    icon: '⛷️',
    title: '最大斜度メモ',
    message:
      '{{resort}}の最大斜度は約 {{course.maxSlope}}度ほど。脚を温めてからチャレンジするのが安心だそうですよ。',
    requires: ['courses'],
    priority: 3,
  },

  // ----- ローカル小ネタ -----
  {
    id: 'curated-local-001',
    category: 'EVENT',
    icon: '💡',
    title: '{{resort}} 豆知識',
    message: '{{localTip}}',
    requires: ['localTips'],
    priority: 5,
  },

  // ============================================================
  // スキルレベル別 Tip
  // skillLevels の指定があれば、対象レベルのスキーヤーにのみ表示される。
  // 「中級者に初心者向け基礎は届けない」など、興味のある情報だけを配信する。
  // ============================================================

  // ----- 初級者 (BEGINNER) 向け -----
  {
    id: 'lv-beginner-001',
    category: 'SCHOOL',
    icon: '🐣',
    title: 'まずは緩斜面に慣れて',
    message:
      '最初の数日は、緩斜面で「止まる・曲がる」を繰り返すと安心して上達できると言われています。焦らず、転んでも起き上がりやすい場所で練習してみてくださいね。',
    skillLevels: ['BEGINNER'],
    priority: 3,
  },
  {
    id: 'lv-beginner-002',
    category: 'SCHOOL',
    icon: '🛷',
    title: '転んだときの起き上がり方',
    message:
      '転んだら、両足を斜面下側に揃えて板を斜面に対し横向きにすると起き上がりやすいそうですよ。あわてず、ストックを支えにしてみてください。',
    skillLevels: ['BEGINNER'],
    priority: 3,
  },
  {
    id: 'lv-beginner-003',
    category: 'LIFT',
    icon: '🪑',
    title: '初めてのリフト乗車',
    message:
      'リフトに乗るときは、係員さんに「初めてです」と一言伝えると、ゆっくり止めてくれることが多いそうです。降りるときは前を見て、両足を揃えて立つと安心と言われています。',
    skillLevels: ['BEGINNER'],
    priority: 3,
  },
  {
    id: 'lv-beginner-004',
    category: 'GEAR',
    icon: '🪵',
    title: 'ストックの長さの目安',
    message:
      'ストックは、グリップを逆さに持って肘が約 90 度になる長さが目安と言われています。レンタル時に係員さんに相談すると合わせてもらえるそうですよ。',
    skillLevels: ['BEGINNER'],
    priority: 2,
  },
  {
    id: 'lv-beginner-005',
    category: 'SAFETY',
    icon: '🦺',
    title: 'はじめは半日で十分',
    message:
      '初めての日は半日くらいで切り上げて、温泉や食事を楽しむのも良いと言われています。脚の疲労が翌日のケガを防ぐ、と現地のインストラクターに教わることが多いそうです。',
    skillLevels: ['BEGINNER'],
    priority: 2,
  },

  // ----- 中級者 (INTERMEDIATE) 向け -----
  {
    id: 'lv-intermediate-001',
    category: 'SCHOOL',
    icon: '🌀',
    title: 'パラレルへの一歩',
    message:
      'プルークから抜け出すには、ターン後半で外足にしっかり体重を乗せる感覚を意識すると良いと言われています。少し急めの中斜面でリズム良く繰り返すのがコツだそうです。',
    skillLevels: ['INTERMEDIATE'],
    priority: 3,
  },
  {
    id: 'lv-intermediate-002',
    category: 'SCHOOL',
    icon: '🎯',
    title: 'カービングの感覚',
    message:
      '板を「滑らせる」より「踏んで撓ませる」意識を持つと、カービングの一歩目に近づくと言われています。緩斜面でゆっくり試すと感覚をつかみやすいそうですよ。',
    skillLevels: ['INTERMEDIATE'],
    priority: 3,
  },
  {
    id: 'lv-intermediate-003',
    category: 'LIFT',
    icon: '🗺️',
    title: '中斜面で 1 日メリハリ',
    message:
      '同じ中斜面ばかりではなく、午前は技術系の中斜面、午後はロングコースで流す、と組み合わせるのが上達と楽しさのバランスが良いと言われています。',
    skillLevels: ['INTERMEDIATE'],
    priority: 2,
  },
  {
    id: 'lv-intermediate-004',
    category: 'GEAR',
    icon: '⚙️',
    title: '板のチョイスを見直す',
    message:
      'オールラウンド板に慣れたら、少し硬めの板に乗り換えると新しい発見があると言われています。試乗会や上位グレードのレンタルで気軽に試せるそうですよ。',
    skillLevels: ['INTERMEDIATE'],
    priority: 2,
  },

  // ----- 上級者 (ADVANCED) 向け -----
  {
    id: 'lv-advanced-001',
    category: 'SCHOOL',
    icon: '🏂',
    title: 'コブの入り方',
    message:
      'コブは「コブの裏側にライン取り」を意識すると、リズムが取りやすいと言われています。最初は浅いコブで、ストックワークと膝の屈伸を合わせる練習が定番だそうですよ。',
    skillLevels: ['ADVANCED', 'EXPERT'],
    priority: 3,
  },
  {
    id: 'lv-advanced-002',
    category: 'SNOW',
    icon: '🏔️',
    title: '不整地での体重移動',
    message:
      '不整地では体軸を内側に倒し過ぎず、骨盤を進行方向にキープすると安定すると言われています。脚の屈伸でショックを吸収するのがコツだそうです。',
    skillLevels: ['ADVANCED', 'EXPERT'],
    priority: 3,
  },
  {
    id: 'lv-advanced-003',
    category: 'LIFT',
    icon: '⏱️',
    title: '混雑時間外の山頂アタック',
    message:
      '上級コースの山頂エリアは、12:00 前後がやや空きやすい時間帯と言われています。お昼を 13:00 にずらすと、人の少ないバーンを楽しめることが多いそうですよ。',
    skillLevels: ['ADVANCED', 'EXPERT'],
    priority: 2,
  },
  {
    id: 'lv-advanced-004',
    category: 'GEAR',
    icon: '🪛',
    title: 'ビンディング解放値の見直し',
    message:
      'ビンディングの解放値は、体重・滑走スタイル・年齢で目安が決まるそうです。シーズン頭にショップで再調整してもらうと、トラブル予防になると言われています。',
    skillLevels: ['ADVANCED', 'EXPERT'],
    priority: 2,
  },

  // ----- エキスパート (EXPERT) 向け -----
  {
    id: 'lv-expert-001',
    category: 'SAFETY',
    icon: '🎒',
    title: 'バックカントリー前のチェック',
    message:
      'バックカントリーに入る前には、ビーコン・ショベル・プローブの 3 点と、当日の雪崩情報の確認が基本と言われています。単独行動は避け、信頼できる仲間と一緒に行動するのが鉄則だそうです。',
    skillLevels: ['EXPERT'],
    priority: 4,
  },
  {
    id: 'lv-expert-002',
    category: 'SNOW',
    icon: '🌲',
    title: 'ツリーランの安全基礎',
    message:
      'ツリーランは木の周辺の「ツリーホール」が見えにくい雪崩リスクと言われています。ストックを長めに持ち、木と木の間隔が広いラインを選ぶのが基本だそうです。',
    skillLevels: ['EXPERT'],
    priority: 3,
  },
  {
    id: 'lv-expert-003',
    category: 'GEAR',
    icon: '⛷️',
    title: 'パウダー専用板の見立て',
    message:
      'パウダー専用板は通常より 5〜10cm 長めを選ぶと浮力が出やすいと言われています。ロッカー形状とノーズの太さがコース選びの幅を広げてくれるそうですよ。',
    skillLevels: ['EXPERT'],
    priority: 2,
  },
  {
    id: 'lv-expert-004',
    category: 'EVENT',
    icon: '🏁',
    title: 'パークセッションのマナー',
    message:
      'パークでは、アイテムに入る前に「ドロップ！」のコールが共通マナーと言われています。前の人の着地を見届けてから入ると、お互い気持ちよくセッションできるそうです。',
    skillLevels: ['EXPERT'],
    priority: 2,
  },
];
