"""Second batch: Portuguese, Polish, Turkish, Chinese, Japanese, Korean."""

import io
import os
import re

ROOT = r"D:\projects\moonlight-android"
RES = os.path.join(ROOT, "app", "src", "main", "res")

TRANSLATIONS = {}

TRANSLATIONS["values-pt-rBR"] = """
back\tVoltar
title_universal_layouts\tLayouts na tela
title_add_layout\tAdicionar layout
title_layout_image\tImagem do layout
title_edit_controls\tEditar controles
title_reset_controls\tRedefinir
title_pick_image\tImagem
title_use_default_image\tUsar padrão
msg_layouts_help\tEscolha o layout usado durante a transmissão. Toque em um layout para editar ou remover; o botão de mais adiciona um novo.
msg_no_layouts\tAinda não há layouts. Toque no mais para criar um.
msg_layout_selected\tSelecionado
msg_no_images\tNenhuma imagem encontrada neste dispositivo.
msg_image_permission_needed\tO acesso às suas imagens não foi concedido, então não há nada para escolher. Conceda a permissão e tente de novo.
msg_image_picker_failed\tNão foi possível carregar as imagens deste dispositivo.
dialog_title_layout_name\tNome do layout
hint_layout_name\tNome do layout
menu_edit_layout\tEditar
menu_choose_image\tTrocar imagem
menu_use_layout\tUsar este layout
menu_delete_layout\tExcluir
dialog_text_reset_layout_controls\tRedefinir o tamanho e a posição dos controles deste layout?
toast_layout_controls_reset\tControles redefinidos
toast_layout_adjusted_to_fit\tEste layout foi montado para outro tamanho de tela e foi ajustado a esta. Use «Redefinir» para voltar ao visual original.
title_layout_settings\tConfigurações do layout
control_type_button\tBotão
control_type_stick\tAnalógico
control_type_dpad\tDirecional
control_type_element\tElemento
editor_label_center_button\tBotão central
editor_label_corner_radius\tRaio dos cantos
editor_section_center_button\tCentro do analógico
editor_label_center_button_enabled\tTocar no centro funciona como botão
editor_label_center_button_action\tO que o centro envia
menu_add_button\tAdicionar botão
menu_add_stick\tAdicionar analógico
menu_add_dpad\tAdicionar direcional
menu_save\tSalvar
menu_save_as\tSalvar como
menu_exit\tSair
menu_duplicate\tDuplicar
menu_copy_size\tCopiar tamanho
menu_paste_size\tColar tamanho
menu_edit\tEditar
menu_delete\tExcluir
dialog_title_delete_control\tExcluir «%1$s»?
stream_menu_title\tMenu da transmissão
stream_menu_keyboard\tMostrar teclado
stream_menu_touch\tEntrada por toque
stream_menu_touch_off\tDesligada
stream_menu_touch_trackpad\tTouchpad
stream_menu_touch_right_stick\tAnalógico direito
stream_menu_touch_left_stick\tAnalógico esquerdo
stream_menu_touch_events\tToque real (experimental)
stream_menu_opacity\tOpacidade
stream_menu_sensitivity\tSensibilidade
stream_menu_touchpad_mode\tModo do touchpad
stream_menu_touchpad_standard\tTouchpad
stream_menu_touchpad_direct\tCursor sob o dedo
stream_menu_touchpad_no_clicks\tSem cliques
stream_menu_extras\tExtras
stream_menu_vibration\tVibração
stream_menu_controls\tControles na tela
editor_label_pass_through\tPassar para o toque (segure e mova para olhar em volta)
stream_menu_layout\tLayout
stream_menu_exit\tSair da transmissão
stream_menu_layout_next_time\t«%1$s» será usado na próxima transmissão
dialog_title_save_changes\tSalvar as alterações?
dialog_text_save_changes\tSalvar as alterações deste layout?
menu_dont_save\tNão salvar
dialog_title_save_layout_as\tSalvar layout como
hint_layout_name_new\tNome do novo layout
toast_layout_saved\tLayout salvo
toast_layout_saved_as\tSalvo como "%s"
toast_element_added\tAdicionado. Arraste para o lugar.
editor_section_element\tElemento
editor_section_position\tPosição e tamanho
editor_section_action\tAção
editor_section_actions\tEste elemento
menu_share_layout\tCompartilhar layout
title_import_layout\tImportar layout
import_failed\tNão foi possível ler esse arquivo
import_done\t«%1$s» importado
import_done_rescaled\t«%1$s» importado, ajustado a esta tela
editor_label_name\tNome
editor_label_x\tX
editor_label_y\tY
editor_label_width\tLargura
editor_label_height\tAltura
editor_label_toggle\tTrava (toque para pressionar, toque de novo para soltar)
editor_toggle_off_while_pass_through\tA trava fica desligada enquanto o repasse ao toque está ativo
editor_label_sequence\tSequência de eventos
editor_label_sequence_empty\tAinda não há eventos. Adicione um passo abaixo.
editor_label_step_delay\tAtraso após o passo (ms)
editor_label_control_mode\tModo de operação
editor_mode_axes\tEixos
editor_mode_buttons\tBotões
editor_label_invert_y\tInverter cima e baixo (se o jogo lê ao contrário)
editor_label_axis_x\tEixo X
editor_label_axis_y\tEixo Y
editor_label_direction_up\tBotão: cima
editor_label_direction_down\tBotão: baixo
editor_label_direction_left\tBotão: esquerda
editor_label_direction_right\tBotão: direita
editor_add_step\tAdicionar passo
editor_add_event\tAdicionar evento
editor_remove_step\tRemover passo
editor_step_title\tPasso %d
editor_pick_device\tTipo de evento
editor_device_keyboard\tTeclado
editor_device_mouse\tMouse
editor_device_gamepad\tControle
editor_pick_key\tTecla
editor_pick_button\tBotão
editor_remove_event\tRemover
editor_label_direction_action\tAção para %s
category_about\tSobre
title_open_source_licenses\tLicenças de código aberto
summary_open_source_licenses\tA licença deste aplicativo e as do que ele é feito
"""

TRANSLATIONS["values-pl"] = """
back\tWstecz
title_universal_layouts\tUkłady na ekranie
title_add_layout\tDodaj układ
title_layout_image\tObraz układu
title_edit_controls\tEdytuj przyciski
title_reset_controls\tPrzywróć
title_pick_image\tObraz
title_use_default_image\tUżyj domyślnego
msg_layouts_help\tWybierz układ używany podczas transmisji. Dotknij układu, aby go zmienić lub usunąć; nowy dodasz plusem.
msg_no_layouts\tNie ma jeszcze układów. Dotknij plusa, aby utworzyć.
msg_layout_selected\tWybrany
msg_no_images\tNie znaleziono obrazów na tym urządzeniu.
msg_image_permission_needed\tDostęp do obrazów nie został przyznany, więc nie ma czego wybierać. Przyznaj dostęp i spróbuj ponownie.
msg_image_picker_failed\tNie udało się wczytać obrazów z tego urządzenia.
dialog_title_layout_name\tNazwa układu
hint_layout_name\tNazwa układu
menu_edit_layout\tZmień
menu_choose_image\tZmień obraz
menu_use_layout\tUżyj tego układu
menu_delete_layout\tUsuń
dialog_text_reset_layout_controls\tPrzywrócić domyślny rozmiar i położenie przycisków tego układu?
toast_layout_controls_reset\tPrzyciski przywrócone do domyślnych
toast_layout_adjusted_to_fit\tTen układ powstał dla innego rozmiaru ekranu i został dopasowany do tego. Użyj „Przywróć”, aby wrócić do pierwotnego wyglądu.
title_layout_settings\tUstawienia układu
control_type_button\tPrzycisk
control_type_stick\tDrążek
control_type_dpad\tKrzyżak
control_type_element\tElement
editor_label_center_button\tŚrodkowy przycisk
editor_label_corner_radius\tZaokrąglenie
editor_section_center_button\tŚrodek drążka
editor_label_center_button_enabled\tDotknięcie środka działa jak przycisk
editor_label_center_button_action\tCo wysyła środek
menu_add_button\tDodaj przycisk
menu_add_stick\tDodaj drążek
menu_add_dpad\tDodaj krzyżak
menu_save\tZapisz
menu_save_as\tZapisz jako
menu_exit\tWyjdź
menu_duplicate\tDuplikuj
menu_copy_size\tKopiuj rozmiar
menu_paste_size\tWklej rozmiar
menu_edit\tZmień
menu_delete\tUsuń
dialog_title_delete_control\tUsunąć „%1$s”?
stream_menu_title\tMenu transmisji
stream_menu_keyboard\tPokaż klawiaturę
stream_menu_touch\tWejście dotykiem
stream_menu_touch_off\tWyłączone
stream_menu_touch_trackpad\tPanel dotykowy
stream_menu_touch_right_stick\tPrawy drążek
stream_menu_touch_left_stick\tLewy drążek
stream_menu_touch_events\tPrawdziwy dotyk (eksperyment)
stream_menu_opacity\tKrycie
stream_menu_sensitivity\tCzułość
stream_menu_touchpad_mode\tTryb panelu dotykowego
stream_menu_touchpad_standard\tPanel dotykowy
stream_menu_touchpad_direct\tKursor pod palcem
stream_menu_touchpad_no_clicks\tBez kliknięć
stream_menu_extras\tDodatki
stream_menu_vibration\tWibracja
stream_menu_controls\tPrzyciski na ekranie
editor_label_pass_through\tPrzekazuj do dotyku (przytrzymaj i prowadź, aby się rozglądać)
stream_menu_layout\tUkład
stream_menu_exit\tZakończ transmisję
stream_menu_layout_next_time\t„%1$s” zostanie użyty przy następnej transmisji
dialog_title_save_changes\tZapisać zmiany?
dialog_text_save_changes\tZapisać zmiany w tym układzie?
menu_dont_save\tNie zapisuj
dialog_title_save_layout_as\tZapisz układ jako
hint_layout_name_new\tNazwa nowego układu
toast_layout_saved\tUkład zapisany
toast_layout_saved_as\tZapisano jako "%s"
toast_element_added\tDodano. Przeciągnij na miejsce.
editor_section_element\tElement
editor_section_position\tPołożenie i rozmiar
editor_section_action\tDziałanie
editor_section_actions\tTen element
menu_share_layout\tUdostępnij układ
title_import_layout\tImportuj układ
import_failed\tNie udało się odczytać tego pliku
import_done\tZaimportowano „%1$s”
import_done_rescaled\tZaimportowano „%1$s”, dopasowano do tego ekranu
editor_label_name\tNazwa
editor_label_x\tX
editor_label_y\tY
editor_label_width\tSzerokość
editor_label_height\tWysokość
editor_label_toggle\tZatrzask (dotknij, aby nacisnąć, dotknij ponownie, aby puścić)
editor_toggle_off_while_pass_through\tZatrzask jest wyłączony, gdy przekazywanie do dotyku jest włączone
editor_label_sequence\tSekwencja zdarzeń
editor_label_sequence_empty\tBrak zdarzeń. Dodaj krok poniżej.
editor_label_step_delay\tOpóźnienie po kroku (ms)
editor_label_control_mode\tTryb pracy
editor_mode_axes\tOsie
editor_mode_buttons\tPrzyciski
editor_label_invert_y\tOdwróć górę i dół (jeśli gra czyta to na odwrót)
editor_label_axis_x\tOś X
editor_label_axis_y\tOś Y
editor_label_direction_up\tPrzycisk: góra
editor_label_direction_down\tPrzycisk: dół
editor_label_direction_left\tPrzycisk: lewo
editor_label_direction_right\tPrzycisk: prawo
editor_add_step\tDodaj krok
editor_add_event\tDodaj zdarzenie
editor_remove_step\tUsuń krok
editor_step_title\tKrok %d
editor_pick_device\tRodzaj zdarzenia
editor_device_keyboard\tKlawiatura
editor_device_mouse\tMysz
editor_device_gamepad\tPad
editor_pick_key\tKlawisz
editor_pick_button\tPrzycisk
editor_remove_event\tUsuń
editor_label_direction_action\tDziałanie dla %s
category_about\tO aplikacji
title_open_source_licenses\tLicencje open source
summary_open_source_licenses\tLicencja tej aplikacji i licencje tego, z czego jest zbudowana
"""

TRANSLATIONS["values-tr"] = """
back\tGeri
title_universal_layouts\tEkran düzenleri
title_add_layout\tDüzen ekle
title_layout_image\tDüzen resmi
title_edit_controls\tDenetimleri düzenle
title_reset_controls\tSıfırla
title_pick_image\tResim
title_use_default_image\tVarsayılanı kullan
msg_layouts_help\tYayın sırasında kullanılacak düzeni seçin. Bir düzeni düzenlemek veya silmek için dokunun; yenisini artı düğmesi ekler.
msg_no_layouts\tHenüz düzen yok. Oluşturmak için artıya dokunun.
msg_layout_selected\tSeçili
msg_no_images\tBu cihazda resim bulunamadı.
msg_image_permission_needed\tResimlerinize erişim verilmediği için seçilecek bir şey yok. İzin verip yeniden deneyin.
msg_image_picker_failed\tBu cihazdaki resimler yüklenemedi.
dialog_title_layout_name\tDüzen adı
hint_layout_name\tDüzen adı
menu_edit_layout\tDüzenle
menu_choose_image\tResmi değiştir
menu_use_layout\tBu düzeni kullan
menu_delete_layout\tSil
dialog_text_reset_layout_controls\tBu düzenin denetimleri varsayılan boyut ve konuma dönsün mü?
toast_layout_controls_reset\tDenetimler varsayılana döndürüldü
toast_layout_adjusted_to_fit\tBu düzen başka bir ekran boyutu için hazırlanmıştı ve bu ekrana uyarlandı. Özgün görünüm için «Sıfırla»yı kullanın.
title_layout_settings\tDüzen ayarları
control_type_button\tDüğme
control_type_stick\tÇubuk
control_type_dpad\tYön tuşları
control_type_element\tÖğe
editor_label_center_button\tOrta düğme
editor_label_corner_radius\tKöşe yarıçapı
editor_section_center_button\tÇubuğun ortası
editor_label_center_button_enabled\tOrtaya dokunmak düğme gibi çalışır
editor_label_center_button_action\tOrtanın gönderdiği
menu_add_button\tDüğme ekle
menu_add_stick\tÇubuk ekle
menu_add_dpad\tYön tuşları ekle
menu_save\tKaydet
menu_save_as\tFarklı kaydet
menu_exit\tÇık
menu_duplicate\tÇoğalt
menu_copy_size\tBoyutu kopyala
menu_paste_size\tBoyutu yapıştır
menu_edit\tDüzenle
menu_delete\tSil
dialog_title_delete_control\t«%1$s» silinsin mi?
stream_menu_title\tYayın menüsü
stream_menu_keyboard\tKlavyeyi göster
stream_menu_touch\tDokunma girişi
stream_menu_touch_off\tKapalı
stream_menu_touch_trackpad\tDokunmatik yüzey
stream_menu_touch_right_stick\tSağ çubuk
stream_menu_touch_left_stick\tSol çubuk
stream_menu_touch_events\tGerçek dokunma (deneysel)
stream_menu_opacity\tSaydamlık
stream_menu_sensitivity\tHassasiyet
stream_menu_touchpad_mode\tDokunmatik yüzey modu
stream_menu_touchpad_standard\tDokunmatik yüzey
stream_menu_touchpad_direct\tParmak altında imleç
stream_menu_touchpad_no_clicks\tTıklamasız
stream_menu_extras\tEk seçenekler
stream_menu_vibration\tTitreşim
stream_menu_controls\tEkran denetimleri
editor_label_pass_through\tDokunmaya aktar (basılı tutup sürükleyerek çevrene bak)
stream_menu_layout\tDüzen
stream_menu_exit\tYayından çık
stream_menu_layout_next_time\t«%1$s» bir sonraki yayında kullanılacak
dialog_title_save_changes\tDeğişiklikler kaydedilsin mi?
dialog_text_save_changes\tBu düzendeki değişiklikler kaydedilsin mi?
menu_dont_save\tKaydetme
dialog_title_save_layout_as\tDüzeni farklı kaydet
hint_layout_name_new\tYeni düzenin adı
toast_layout_saved\tDüzen kaydedildi
toast_layout_saved_as\t"%s" olarak kaydedildi
toast_element_added\tEklendi. Yerine sürükleyin.
editor_section_element\tÖğe
editor_section_position\tKonum ve boyut
editor_section_action\tEylem
editor_section_actions\tBu öğe
menu_share_layout\tDüzeni paylaş
title_import_layout\tDüzen içe aktar
import_failed\tBu dosya okunamadı
import_done\t«%1$s» içe aktarıldı
import_done_rescaled\t«%1$s» içe aktarıldı, bu ekrana uyarlandı
editor_label_name\tAd
editor_label_x\tX
editor_label_y\tY
editor_label_width\tGenişlik
editor_label_height\tYükseklik
editor_label_toggle\tKilitleme (dokun: basılı tut, tekrar dokun: bırak)
editor_toggle_off_while_pass_through\tDokunmaya aktarma açıkken kilitleme kapalıdır
editor_label_sequence\tOlay dizisi
editor_label_sequence_empty\tHenüz olay yok. Aşağıya bir adım ekleyin.
editor_label_step_delay\tAdımdan sonra gecikme (ms)
editor_label_control_mode\tÇalışma modu
editor_mode_axes\tEksenler
editor_mode_buttons\tDüğmeler
editor_label_invert_y\tÜst ve altı ters çevir (oyun ters okuyorsa)
editor_label_axis_x\tX ekseni
editor_label_axis_y\tY ekseni
editor_label_direction_up\tDüğme: yukarı
editor_label_direction_down\tDüğme: aşağı
editor_label_direction_left\tDüğme: sol
editor_label_direction_right\tDüğme: sağ
editor_add_step\tAdım ekle
editor_add_event\tOlay ekle
editor_remove_step\tAdımı kaldır
editor_step_title\t%d. adım
editor_pick_device\tOlay türü
editor_device_keyboard\tKlavye
editor_device_mouse\tFare
editor_device_gamepad\tOyun kolu
editor_pick_key\tTuş
editor_pick_button\tDüğme
editor_remove_event\tKaldır
editor_label_direction_action\t%s için eylem
category_about\tUygulama hakkında
title_open_source_licenses\tAçık kaynak lisansları
summary_open_source_licenses\tBu uygulamanın lisansı ve derlendiği bileşenlerin lisansları
"""

TRANSLATIONS["values-zh-rCN"] = """
back\t返回
title_universal_layouts\t屏幕布局
title_add_layout\t添加布局
title_layout_image\t布局图片
title_edit_controls\t编辑按键
title_reset_controls\t重置
title_pick_image\t图片
title_use_default_image\t使用默认
msg_layouts_help\t选择串流时使用的布局。点按布局可编辑或删除；加号按钮可新建布局。
msg_no_layouts\t还没有布局。点按加号创建一个。
msg_layout_selected\t已选择
msg_no_images\t此设备上没有找到图片。
msg_image_permission_needed\t未获得图片访问权限，因此没有可选的图片。请授予权限后重试。
msg_image_picker_failed\t无法从此设备加载图片。
dialog_title_layout_name\t布局名称
hint_layout_name\t布局名称
menu_edit_layout\t编辑
menu_choose_image\t更换图片
menu_use_layout\t使用此布局
menu_delete_layout\t删除
dialog_text_reset_layout_controls\t将此布局的按键恢复到默认大小和位置？
toast_layout_controls_reset\t按键已恢复默认
toast_layout_adjusted_to_fit\t此布局是为另一种屏幕尺寸排列的，已按当前屏幕缩放。点按「重置」可恢复原始外观。
title_layout_settings\t布局设置
control_type_button\t按键
control_type_stick\t摇杆
control_type_dpad\t十字键
control_type_element\t元素
editor_label_center_button\t中间按键
editor_label_corner_radius\t圆角
editor_section_center_button\t摇杆中心
editor_label_center_button_enabled\t点按中心等于按下按键
editor_label_center_button_action\t中心发送的内容
menu_add_button\t添加按键
menu_add_stick\t添加摇杆
menu_add_dpad\t添加十字键
menu_save\t保存
menu_save_as\t另存为
menu_exit\t退出
menu_duplicate\t复制
menu_copy_size\t复制尺寸
menu_paste_size\t粘贴尺寸
menu_edit\t编辑
menu_delete\t删除
dialog_title_delete_control\t删除「%1$s」？
stream_menu_title\t串流菜单
stream_menu_keyboard\t显示键盘
stream_menu_touch\t触摸输入
stream_menu_touch_off\t关闭
stream_menu_touch_trackpad\t触摸板
stream_menu_touch_right_stick\t右摇杆
stream_menu_touch_left_stick\t左摇杆
stream_menu_touch_events\t真实触摸（实验）
stream_menu_opacity\t不透明度
stream_menu_sensitivity\t灵敏度
stream_menu_touchpad_mode\t触摸板模式
stream_menu_touchpad_standard\t触摸板
stream_menu_touchpad_direct\t手指下的光标
stream_menu_touchpad_no_clicks\t不点击
stream_menu_extras\t更多
stream_menu_vibration\t振动
stream_menu_controls\t屏幕按键
editor_label_pass_through\t透传到触摸（按住并移动来环顾四周）
stream_menu_layout\t布局
stream_menu_exit\t退出串流
stream_menu_layout_next_time\t下次串流将使用「%1$s」
dialog_title_save_changes\t保存更改？
dialog_text_save_changes\t保存对此布局的更改？
menu_dont_save\t不保存
dialog_title_save_layout_as\t布局另存为
hint_layout_name_new\t新布局的名称
toast_layout_saved\t布局已保存
toast_layout_saved_as\t已保存为"%s"
toast_element_added\t已添加。拖动到合适的位置。
editor_section_element\t元素
editor_section_position\t位置和大小
editor_section_action\t动作
editor_section_actions\t此元素
menu_share_layout\t分享布局
title_import_layout\t导入布局
import_failed\t无法读取该文件
import_done\t已导入「%1$s」
import_done_rescaled\t已导入「%1$s」，已适配此屏幕
editor_label_name\t名称
editor_label_x\tX
editor_label_y\tY
editor_label_width\t宽度
editor_label_height\t高度
editor_label_toggle\t锁定（点按按下，再次点按松开）
editor_toggle_off_while_pass_through\t开启透传时锁定不可用
editor_label_sequence\t事件序列
editor_label_sequence_empty\t还没有事件。在下面添加步骤。
editor_label_step_delay\t步骤后延迟（毫秒）
editor_label_control_mode\t工作模式
editor_mode_axes\t轴
editor_mode_buttons\t按键
editor_label_invert_y\t反转上下（如果游戏读取方向相反）
editor_label_axis_x\tX 轴
editor_label_axis_y\tY 轴
editor_label_direction_up\t按键：上
editor_label_direction_down\t按键：下
editor_label_direction_left\t按键：左
editor_label_direction_right\t按键：右
editor_add_step\t添加步骤
editor_add_event\t添加事件
editor_remove_step\t删除步骤
editor_step_title\t步骤 %d
editor_pick_device\t事件类型
editor_device_keyboard\t键盘
editor_device_mouse\t鼠标
editor_device_gamepad\t手柄
editor_pick_key\t按键
editor_pick_button\t按钮
editor_remove_event\t移除
editor_label_direction_action\t%s 的动作
category_about\t关于
title_open_source_licenses\t开源许可
summary_open_source_licenses\t本应用的许可证，以及它所使用的组件的许可证
"""

TRANSLATIONS["values-ja"] = """
back\t戻る
title_universal_layouts\t画面上のレイアウト
title_add_layout\tレイアウトを追加
title_layout_image\tレイアウト画像
title_edit_controls\tコントロールを編集
title_reset_controls\tリセット
title_pick_image\t画像
title_use_default_image\t既定を使う
msg_layouts_help\tストリーミング中に使うレイアウトを選びます。レイアウトをタップすると編集または削除できます。新規作成はプラスボタンから。
msg_no_layouts\tレイアウトがまだありません。プラスをタップして作成します。
msg_layout_selected\t選択中
msg_no_images\tこの端末に画像が見つかりません。
msg_image_permission_needed\t画像へのアクセスが許可されていないため、選べるものがありません。許可してから再試行してください。
msg_image_picker_failed\tこの端末から画像を読み込めませんでした。
dialog_title_layout_name\tレイアウト名
hint_layout_name\tレイアウト名
menu_edit_layout\t編集
menu_choose_image\t画像を変更
menu_use_layout\tこのレイアウトを使う
menu_delete_layout\t削除
dialog_text_reset_layout_controls\tこのレイアウトのコントロールを既定の大きさと位置に戻しますか？
toast_layout_controls_reset\tコントロールを既定に戻しました
toast_layout_adjusted_to_fit\tこのレイアウトは別の画面サイズ用に作られており、この画面に合わせて調整されました。元の見た目に戻すには「リセット」を使ってください。
title_layout_settings\tレイアウト設定
control_type_button\tボタン
control_type_stick\tスティック
control_type_dpad\t十字キー
control_type_element\t要素
editor_label_center_button\t中央ボタン
editor_label_corner_radius\t角の丸み
editor_section_center_button\tスティックの中央
editor_label_center_button_enabled\t中央をタップするとボタンとして動作
editor_label_center_button_action\t中央が送るもの
menu_add_button\tボタンを追加
menu_add_stick\tスティックを追加
menu_add_dpad\t十字キーを追加
menu_save\t保存
menu_save_as\t名前を付けて保存
menu_exit\t終了
menu_duplicate\t複製
menu_copy_size\t大きさをコピー
menu_paste_size\t大きさを貼り付け
menu_edit\t編集
menu_delete\t削除
dialog_title_delete_control\t「%1$s」を削除しますか？
stream_menu_title\tストリームメニュー
stream_menu_keyboard\tキーボードを表示
stream_menu_touch\tタッチ入力
stream_menu_touch_off\tオフ
stream_menu_touch_trackpad\tタッチパッド
stream_menu_touch_right_stick\t右スティック
stream_menu_touch_left_stick\t左スティック
stream_menu_touch_events\t実際のタッチ（実験的）
stream_menu_opacity\t不透明度
stream_menu_sensitivity\t感度
stream_menu_touchpad_mode\tタッチパッドのモード
stream_menu_touchpad_standard\tタッチパッド
stream_menu_touchpad_direct\t指の下のカーソル
stream_menu_touchpad_no_clicks\tクリックなし
stream_menu_extras\tその他
stream_menu_vibration\t振動
stream_menu_controls\t画面上のコントロール
editor_label_pass_through\tタッチに渡す（押したまま動かして見回す）
stream_menu_layout\tレイアウト
stream_menu_exit\tストリーミングを終了
stream_menu_layout_next_time\t「%1$s」は次回のストリーミングで使われます
dialog_title_save_changes\t変更を保存しますか？
dialog_text_save_changes\tこのレイアウトの変更を保存しますか？
menu_dont_save\t保存しない
dialog_title_save_layout_as\tレイアウトに名前を付けて保存
hint_layout_name_new\t新しいレイアウトの名前
toast_layout_saved\tレイアウトを保存しました
toast_layout_saved_as\t"%s"として保存しました
toast_element_added\t追加しました。所定の位置へドラッグしてください。
editor_section_element\t要素
editor_section_position\t位置と大きさ
editor_section_action\t動作
editor_section_actions\tこの要素
menu_share_layout\tレイアウトを共有
title_import_layout\tレイアウトを読み込む
import_failed\tそのファイルを読み込めませんでした
import_done\t「%1$s」を読み込みました
import_done_rescaled\t「%1$s」を読み込み、この画面に合わせました
editor_label_name\t名前
editor_label_x\tX
editor_label_y\tY
editor_label_width\t幅
editor_label_height\t高さ
editor_label_toggle\t固定（タップで押し、もう一度タップで離す）
editor_toggle_off_while_pass_through\tタッチへ渡すが有効な間は固定を使えません
editor_label_sequence\tイベントの並び
editor_label_sequence_empty\tイベントはまだありません。下でステップを追加してください。
editor_label_step_delay\tステップ後の待ち時間（ミリ秒）
editor_label_control_mode\t動作モード
editor_mode_axes\t軸
editor_mode_buttons\tボタン
editor_label_invert_y\t上下を反転（ゲームが逆に読む場合）
editor_label_axis_x\tX 軸
editor_label_axis_y\tY 軸
editor_label_direction_up\tボタン：上
editor_label_direction_down\tボタン：下
editor_label_direction_left\tボタン：左
editor_label_direction_right\tボタン：右
editor_add_step\tステップを追加
editor_add_event\tイベントを追加
editor_remove_step\tステップを削除
editor_step_title\tステップ %d
editor_pick_device\tイベントの種類
editor_device_keyboard\tキーボード
editor_device_mouse\tマウス
editor_device_gamepad\tゲームパッド
editor_pick_key\tキー
editor_pick_button\tボタン
editor_remove_event\t外す
editor_label_direction_action\t%s の動作
category_about\tこのアプリについて
title_open_source_licenses\tオープンソースライセンス
summary_open_source_licenses\tこのアプリのライセンスと、構成するもののライセンス
"""

TRANSLATIONS["values-ko"] = """
back\t뒤로
title_universal_layouts\t화면 레이아웃
title_add_layout\t레이아웃 추가
title_layout_image\t레이아웃 이미지
title_edit_controls\t컨트롤 편집
title_reset_controls\t초기화
title_pick_image\t이미지
title_use_default_image\t기본값 사용
msg_layouts_help\t스트리밍 중에 사용할 레이아웃을 고르세요. 레이아웃을 탭하면 편집하거나 삭제할 수 있고, 새 레이아웃은 더하기 버튼으로 만듭니다.
msg_no_layouts\t아직 레이아웃이 없습니다. 더하기를 눌러 만드세요.
msg_layout_selected\t선택됨
msg_no_images\t이 기기에서 이미지를 찾지 못했습니다.
msg_image_permission_needed\t이미지 접근이 허용되지 않아 고를 것이 없습니다. 권한을 허용한 뒤 다시 시도하세요.
msg_image_picker_failed\t이 기기에서 이미지를 불러오지 못했습니다.
dialog_title_layout_name\t레이아웃 이름
hint_layout_name\t레이아웃 이름
menu_edit_layout\t편집
menu_choose_image\t이미지 변경
menu_use_layout\t이 레이아웃 사용
menu_delete_layout\t삭제
dialog_text_reset_layout_controls\t이 레이아웃의 컨트롤을 기본 크기와 위치로 되돌릴까요?
toast_layout_controls_reset\t컨트롤을 기본값으로 되돌렸습니다
toast_layout_adjusted_to_fit\t이 레이아웃은 다른 화면 크기에 맞춰 만들어져 이 화면에 맞게 조정되었습니다. 원래 모습으로 되돌리려면 초기화를 사용하세요.
title_layout_settings\t레이아웃 설정
control_type_button\t버튼
control_type_stick\t스틱
control_type_dpad\t십자 키
control_type_element\t요소
editor_label_center_button\t가운데 버튼
editor_label_corner_radius\t모서리 반경
editor_section_center_button\t스틱 가운데
editor_label_center_button_enabled\t가운데를 누르면 버튼처럼 동작
editor_label_center_button_action\t가운데가 보내는 것
menu_add_button\t버튼 추가
menu_add_stick\t스틱 추가
menu_add_dpad\t십자 키 추가
menu_save\t저장
menu_save_as\t다른 이름으로 저장
menu_exit\t나가기
menu_duplicate\t복제
menu_copy_size\t크기 복사
menu_paste_size\t크기 붙여넣기
menu_edit\t편집
menu_delete\t삭제
dialog_title_delete_control\t"%1$s"을(를) 삭제할까요?
stream_menu_title\t스트리밍 메뉴
stream_menu_keyboard\t키보드 표시
stream_menu_touch\t터치 입력
stream_menu_touch_off\t끔
stream_menu_touch_trackpad\t터치패드
stream_menu_touch_right_stick\t오른쪽 스틱
stream_menu_touch_left_stick\t왼쪽 스틱
stream_menu_touch_events\t실제 터치 (실험적)
stream_menu_opacity\t불투명도
stream_menu_sensitivity\t감도
stream_menu_touchpad_mode\t터치패드 모드
stream_menu_touchpad_standard\t터치패드
stream_menu_touchpad_direct\t손가락 아래 커서
stream_menu_touchpad_no_clicks\t클릭 없음
stream_menu_extras\t기타
stream_menu_vibration\t진동
stream_menu_controls\t화면 컨트롤
editor_label_pass_through\t터치로 넘기기 (누른 채 움직여 둘러보기)
stream_menu_layout\t레이아웃
stream_menu_exit\t스트리밍 종료
stream_menu_layout_next_time\t"%1$s"이(가) 다음 스트리밍에서 사용됩니다
dialog_title_save_changes\t변경 사항을 저장할까요?
dialog_text_save_changes\t이 레이아웃의 변경 사항을 저장할까요?
menu_dont_save\t저장하지 않음
dialog_title_save_layout_as\t레이아웃 다른 이름으로 저장
hint_layout_name_new\t새 레이아웃 이름
toast_layout_saved\t레이아웃을 저장했습니다
toast_layout_saved_as\t"%s"(으)로 저장했습니다
toast_element_added\t추가했습니다. 원하는 위치로 끌어다 놓으세요.
editor_section_element\t요소
editor_section_position\t위치와 크기
editor_section_action\t동작
editor_section_actions\t이 요소
menu_share_layout\t레이아웃 공유
title_import_layout\t레이아웃 가져오기
import_failed\t그 파일을 읽을 수 없습니다
import_done\t"%1$s"을(를) 가져왔습니다
import_done_rescaled\t"%1$s"을(를) 가져와 이 화면에 맞췄습니다
editor_label_name\t이름
editor_label_x\tX
editor_label_y\tY
editor_label_width\t너비
editor_label_height\t높이
editor_label_toggle\t고정 (탭하면 누르고, 다시 탭하면 놓음)
editor_toggle_off_while_pass_through\t터치로 넘기기가 켜져 있는 동안 고정은 꺼집니다
editor_label_sequence\t이벤트 순서
editor_label_sequence_empty\t아직 이벤트가 없습니다. 아래에서 단계를 추가하세요.
editor_label_step_delay\t단계 후 지연 (ms)
editor_label_control_mode\t동작 모드
editor_mode_axes\t축
editor_mode_buttons\t버튼
editor_label_invert_y\t위아래 반전 (게임이 반대로 읽는 경우)
editor_label_axis_x\tX 축
editor_label_axis_y\tY 축
editor_label_direction_up\t버튼: 위
editor_label_direction_down\t버튼: 아래
editor_label_direction_left\t버튼: 왼쪽
editor_label_direction_right\t버튼: 오른쪽
editor_add_step\t단계 추가
editor_add_event\t이벤트 추가
editor_remove_step\t단계 삭제
editor_step_title\t%d단계
editor_pick_device\t이벤트 종류
editor_device_keyboard\t키보드
editor_device_mouse\t마우스
editor_device_gamepad\t게임패드
editor_pick_key\t키
editor_pick_button\t버튼
editor_remove_event\t빼기
editor_label_direction_action\t%s 동작
category_about\t앱 정보
title_open_source_licenses\t오픈 소스 라이선스
summary_open_source_licenses\t이 앱의 라이선스와 이 앱을 구성하는 것들의 라이선스
"""


def escape(value):
    return (value.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;")
            .replace('"', "&quot;").replace("'", "\\'"))


def add(locale, table):
    path = os.path.join(RES, locale, "strings.xml")
    text = io.open(path, encoding="utf-8").read()

    entries = []
    for line in table.strip().split("\n"):
        if not line.strip():
            continue

        key, value = line.split("\t", 1)
        entries.append((key.strip(), value.strip()))

    existing = set(re.findall(r'<string name="([^"]+)"', text))
    missing = [(k, v) for k, v in entries if k not in existing]

    if not missing:
        print(locale, "nothing to add")
        return

    block = "\n".join('    <string name="%s">%s</string>' % (k, escape(v)) for k, v in missing)

    end = text.rindex("</resources>")
    text = text[:end] + block + "\n" + text[end:]

    io.open(path, "w", encoding="utf-8", newline="\n").write(text)
    print("%-16s added %3d" % (locale, len(missing)))


for locale, table in TRANSLATIONS.items():
    add(locale, table)
