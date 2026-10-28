"""Adds the translations of the new on-screen layout features to the locales."""

import io
import os
import re

ROOT = r"D:\projects\moonlight-android"
RES = os.path.join(ROOT, "app", "src", "main", "res")

TRANSLATIONS = {}

TRANSLATIONS["values-ru"] = """
back\tНазад
title_universal_layouts\tЭкранные раскладки
title_add_layout\tДобавить раскладку
title_layout_image\tКартинка раскладки
title_edit_controls\tРедактировать кнопки
title_reset_controls\tСбросить
title_pick_image\tИзображение
title_use_default_image\tПо умолчанию
msg_layouts_help\tВыберите раскладку для трансляции. Нажмите на раскладку, чтобы изменить или удалить её; новая добавляется кнопкой с плюсом.
msg_no_layouts\tРаскладок пока нет. Нажмите плюс, чтобы создать.
msg_layout_selected\tВыбрана
msg_no_images\tНа этом устройстве не найдено изображений.
msg_image_permission_needed\tДоступ к изображениям не разрешён, поэтому выбрать нечего. Разрешите доступ и попробуйте снова.
msg_image_picker_failed\tНе удалось загрузить изображения с этого устройства.
dialog_title_layout_name\tНазвание раскладки
hint_layout_name\tНазвание раскладки
menu_edit_layout\tИзменить
menu_choose_image\tСменить картинку
menu_use_layout\tИспользовать эту раскладку
menu_delete_layout\tУдалить
dialog_text_reset_layout_controls\tВернуть кнопкам этой раскладки размер и положение по умолчанию?
toast_layout_controls_reset\tКнопки возвращены к значениям по умолчанию
toast_layout_adjusted_to_fit\tЭта раскладка была создана для другого размера экрана и подогнана под этот. Нажмите «Сбросить», если нужно вернуть исходный вид.
title_layout_settings\tНастройки раскладки
control_type_button\tКнопка
control_type_stick\tСтик
control_type_dpad\tD-pad
control_type_element\tЭлемент
editor_label_center_button\tСредняя кнопка
editor_label_corner_radius\tСкругление
editor_section_center_button\tСередина стика
editor_label_center_button_enabled\tНажатие по центру работает как кнопка
editor_label_center_button_action\tЧто отправляет центр
menu_add_button\tДобавить кнопку
menu_add_stick\tДобавить стик
menu_add_dpad\tДобавить D-pad
menu_save\tСохранить
menu_save_as\tСохранить как
menu_exit\tВыйти
menu_duplicate\tДублировать
menu_copy_size\tКопировать размер
menu_paste_size\tВставить размер
menu_edit\tИзменить
menu_delete\tУдалить
dialog_title_delete_control\tУдалить «%1$s»?
stream_menu_title\tМеню трансляции
stream_menu_keyboard\tПоказать клавиатуру
stream_menu_touch\tВвод касанием
stream_menu_touch_off\tВыключен
stream_menu_touch_trackpad\tТачпад
stream_menu_touch_right_stick\tПравый стик
stream_menu_touch_left_stick\tЛевый стик
stream_menu_touch_events\tНативный (эксперимент)
stream_menu_opacity\tНепрозрачность
stream_menu_sensitivity\tЧувствительность
stream_menu_touchpad_mode\tРежим тачпада
stream_menu_touchpad_standard\tОбычный
stream_menu_touchpad_direct\tКурсор за пальцем
stream_menu_touchpad_no_clicks\tБез кликов
stream_menu_extras\tЕщё
stream_menu_vibration\tВибрация
stream_menu_controls\tЭкранные кнопки
editor_label_pass_through\tСквозной нажим (удерживайте кнопку и водите пальцем, чтобы осматриваться в режимме тачпада или стика)
stream_menu_layout\tРаскладка
stream_menu_exit\tВыйти из трансляции
stream_menu_layout_next_time\t«%1$s» будет использована при следующей трансляции
dialog_title_save_changes\tСохранить изменения?
dialog_text_save_changes\tСохранить изменения этой раскладки?
menu_dont_save\tНе сохранять
dialog_title_save_layout_as\tСохранить раскладку как
hint_layout_name_new\tНазвание новой раскладки
toast_layout_saved\tРаскладка сохранена
toast_layout_saved_as\tСохранено как &quot;%s&quot;
toast_element_added\tДобавлено. Перетащите на нужное место.
editor_section_element\tЭлемент
editor_section_position\tПоложение и размер
editor_section_action\tДействие
editor_section_actions\tЭтот элемент
menu_share_layout\tПоделиться раскладкой
title_import_layout\tИмпорт раскладки
import_failed\tНе удалось прочитать этот файл
import_done\tИмпортировано «%1$s»
import_done_rescaled\tИмпортировано «%1$s», подогнано под этот экран
editor_label_name\tНазвание
editor_label_x\tX
editor_label_y\tY
editor_label_width\tШирина
editor_label_height\tВысота
editor_label_toggle\tПреключатель (нажатие — включить, ещё одно — выключить)
editor_toggle_off_while_pass_through\tПереключатель выключен, пока включён сквозной нажим
editor_label_sequence\tПоследовательность событий
editor_label_sequence_empty\tСобытий пока нет. Добавьте шаг ниже.
editor_label_step_delay\tЗадержка после шага (мс)
editor_label_control_mode\tРежим работы
editor_mode_axes\tОси
editor_mode_buttons\tКнопки
editor_label_invert_y\tИнвертировать верх и низ (если игра читает наоборот)
editor_label_axis_x\tОсь X
editor_label_axis_y\tОсь Y
editor_label_direction_up\tКнопка: вверх
editor_label_direction_down\tКнопка: вниз
editor_label_direction_left\tКнопка: влево
editor_label_direction_right\tКнопка: вправо
editor_add_step\tДобавить шаг
editor_add_event\tДобавить событие
editor_remove_step\tУдалить шаг
editor_step_title\tШаг %d
editor_pick_device\tТип события
editor_device_keyboard\tКлавиатура
editor_device_mouse\tМышь
editor_device_gamepad\tГеймпад
editor_pick_key\tКлавиша
editor_pick_button\tКнопка
editor_remove_event\tУбрать
editor_label_direction_action\tДействие для %s
category_about\tО приложении
title_open_source_licenses\tЛицензии открытого кода
summary_open_source_licenses\tЛицензия приложения и лицензии того, из чего оно собрано
"""

TRANSLATIONS["values-uk"] = """
back\tНазад
title_universal_layouts\tЕкранні розкладки
title_add_layout\tДодати розкладку
title_layout_image\tКартинка розкладки
title_edit_controls\tРедагувати кнопки
title_reset_controls\tСкинути
title_pick_image\tЗображення
title_use_default_image\tЗа замовчуванням
msg_layouts_help\tВиберіть розкладку для трансляції. Натисніть на розкладку, щоб змінити або видалити її; нова додається кнопкою з плюсом.
msg_no_layouts\tРозкладок поки немає. Натисніть плюс, щоб створити.
msg_layout_selected\tВибрана
msg_no_images\tНа цьому пристрої не знайдено зображень.
msg_image_permission_needed\tДоступ до зображень не надано, тому вибирати нема з чого. Надайте доступ і спробуйте ще раз.
msg_image_picker_failed\tНе вдалося завантажити зображення з цього пристрою.
dialog_title_layout_name\tНазва розкладки
hint_layout_name\tНазва розкладки
menu_edit_layout\tЗмінити
menu_choose_image\tЗмінити картинку
menu_use_layout\tВикористовувати цю розкладку
menu_delete_layout\tВидалити
dialog_text_reset_layout_controls\tПовернути кнопкам цієї розкладки розмір і положення за замовчуванням?
toast_layout_controls_reset\tКнопки повернуто до значень за замовчуванням
toast_layout_adjusted_to_fit\tЦю розкладку створено для іншого розміру екрана й підігнано під цей. Натисніть «Скинути», щоб повернути початковий вигляд.
title_layout_settings\tНалаштування розкладки
control_type_button\tКнопка
control_type_stick\tСтик
control_type_dpad\tD-pad
control_type_element\tЕлемент
editor_label_center_button\tСередня кнопка
editor_label_corner_radius\tЗаокруглення
editor_section_center_button\tСередина стика
editor_label_center_button_enabled\tНатискання по центру працює як кнопка
editor_label_center_button_action\tЩо надсилає центр
menu_add_button\tДодати кнопку
menu_add_stick\tДодати стик
menu_add_dpad\tДодати D-pad
menu_save\tЗберегти
menu_save_as\tЗберегти як
menu_exit\tВийти
menu_duplicate\tДублювати
menu_copy_size\tКопіювати розмір
menu_paste_size\tВставити розмір
menu_edit\tЗмінити
menu_delete\tВидалити
dialog_title_delete_control\tВидалити «%1$s»?
stream_menu_title\tМеню трансляції
stream_menu_keyboard\tПоказати клавіатуру
stream_menu_touch\tВвід дотиком
stream_menu_touch_off\tВимкнений
stream_menu_touch_trackpad\tТачпад
stream_menu_touch_right_stick\tПравий стик
stream_menu_touch_left_stick\tЛівий стик
stream_menu_touch_events\tСправжні дотики (експеримент)
stream_menu_opacity\tПрозорість
stream_menu_sensitivity\tЧутливість
stream_menu_touchpad_mode\tРежим тачпада
stream_menu_touchpad_standard\tЗвичайний
stream_menu_touchpad_direct\tКурсор за пальцем
stream_menu_touchpad_no_clicks\tБез кліків
stream_menu_extras\tЩе
stream_menu_vibration\tВібрація
stream_menu_controls\tЕкранні кнопки
editor_label_pass_through\tПередавати дотик на екран (тримайте й ведіть, щоб оглядатися)
stream_menu_layout\tРозкладка
stream_menu_exit\tВийти з трансляції
stream_menu_layout_next_time\t«%1$s» буде використано під час наступної трансляції
dialog_title_save_changes\tЗберегти зміни?
dialog_text_save_changes\tЗберегти зміни цієї розкладки?
menu_dont_save\tНе зберігати
dialog_title_save_layout_as\tЗберегти розкладку як
hint_layout_name_new\tНазва нової розкладки
toast_layout_saved\tРозкладку збережено
toast_layout_saved_as\tЗбережено як &quot;%s&quot;
toast_element_added\tДодано. Перетягніть на потрібне місце.
editor_section_element\tЕлемент
editor_section_position\tПоложення й розмір
editor_section_action\tДія
editor_section_actions\tЦей елемент
menu_share_layout\tПоділитися розкладкою
title_import_layout\tІмпорт розкладки
import_failed\tНе вдалося прочитати цей файл
import_done\tІмпортовано «%1$s»
import_done_rescaled\tІмпортовано «%1$s», підігнано під цей екран
editor_label_name\tНазва
editor_label_x\tX
editor_label_y\tY
editor_label_width\tШирина
editor_label_height\tВисота
editor_label_toggle\tЗасувка (натискання — увімкнути, ще одне — вимкнути)
editor_toggle_off_while_pass_through\tЗасувка вимкнена, поки ввімкнено наскрізне натискання
editor_label_sequence\tПослідовність подій
editor_label_sequence_empty\tПодій поки немає. Додайте крок нижче.
editor_label_step_delay\tЗатримка після кроку (мс)
editor_label_control_mode\tРежим роботи
editor_mode_axes\tОсі
editor_mode_buttons\tКнопки
editor_label_invert_y\tІнвертувати верх і низ (якщо гра читає навпаки)
editor_label_axis_x\tВісь X
editor_label_axis_y\tВісь Y
editor_label_direction_up\tКнопка: вгору
editor_label_direction_down\tКнопка: вниз
editor_label_direction_left\tКнопка: вліво
editor_label_direction_right\tКнопка: вправо
editor_add_step\tДодати крок
editor_add_event\tДодати подію
editor_remove_step\tВидалити крок
editor_step_title\tКрок %d
editor_pick_device\tТип події
editor_device_keyboard\tКлавіатура
editor_device_mouse\tМиша
editor_device_gamepad\tГеймпад
editor_pick_key\tКлавіша
editor_pick_button\tКнопка
editor_remove_event\tПрибрати
editor_label_direction_action\tДія для %s
category_about\tПро застосунок
title_open_source_licenses\tЛіцензії відкритого коду
summary_open_source_licenses\tЛіцензія застосунку та ліцензії того, з чого він зібраний
"""

TRANSLATIONS["values-de"] = """
back\tZurück
title_universal_layouts\tBildschirmlayouts
title_add_layout\tLayout hinzufügen
title_layout_image\tLayout-Bild
title_edit_controls\tSteuerung bearbeiten
title_reset_controls\tZurücksetzen
title_pick_image\tBild
title_use_default_image\tStandard verwenden
msg_layouts_help\tWähle das Layout für den Stream. Tippe ein Layout an, um es zu bearbeiten oder zu löschen; neue kommen über das Plus hinzu.
msg_no_layouts\tNoch keine Layouts. Tippe auf das Plus, um eines zu erstellen.
msg_layout_selected\tAusgewählt
msg_no_images\tAuf diesem Gerät wurden keine Bilder gefunden.
msg_image_permission_needed\tDer Zugriff auf deine Bilder wurde nicht erlaubt, es gibt also nichts zur Auswahl. Erlaube den Zugriff und versuche es erneut.
msg_image_picker_failed\tDie Bilder konnten von diesem Gerät nicht geladen werden.
dialog_title_layout_name\tLayoutname
hint_layout_name\tLayoutname
menu_edit_layout\tBearbeiten
menu_choose_image\tBild ändern
menu_use_layout\tDieses Layout verwenden
menu_delete_layout\tLöschen
dialog_text_reset_layout_controls\tGröße und Position der Steuerung dieses Layouts zurücksetzen?
toast_layout_controls_reset\tSteuerung zurückgesetzt
toast_layout_adjusted_to_fit\tDieses Layout wurde für eine andere Bildschirmgröße angeordnet und wurde angepasst. Mit „Zurücksetzen“ kommt das ursprüngliche Aussehen zurück.
title_layout_settings\tLayout-Einstellungen
control_type_button\tTaste
control_type_stick\tStick
control_type_dpad\tSteuerkreuz
control_type_element\tElement
editor_label_center_button\tMittlere Taste
editor_label_corner_radius\tEckenradius
editor_section_center_button\tMitte des Sticks
editor_label_center_button_enabled\tTippen auf die Mitte wirkt als Taste
editor_label_center_button_action\tWas die Mitte sendet
menu_add_button\tTaste hinzufügen
menu_add_stick\tStick hinzufügen
menu_add_dpad\tSteuerkreuz hinzufügen
menu_save\tSpeichern
menu_save_as\tSpeichern unter
menu_exit\tBeenden
menu_duplicate\tDuplizieren
menu_copy_size\tGröße kopieren
menu_paste_size\tGröße einfügen
menu_edit\tBearbeiten
menu_delete\tLöschen
dialog_title_delete_control\t&quot;%1$s&quot; löschen?
stream_menu_title\tStream-Menü
stream_menu_keyboard\tTastatur anzeigen
stream_menu_touch\tTouch-Eingabe
stream_menu_touch_off\tAus
stream_menu_touch_trackpad\tTouchpad
stream_menu_touch_right_stick\tRechter Stick
stream_menu_touch_left_stick\tLinker Stick
stream_menu_touch_events\tEchtes Touch (experimentell)
stream_menu_opacity\tDeckkraft
stream_menu_sensitivity\tEmpfindlichkeit
stream_menu_touchpad_mode\tTouchpad-Modus
stream_menu_touchpad_standard\tTouchpad
stream_menu_touchpad_direct\tZeiger am Finger
stream_menu_touchpad_no_clicks\tOhne Klicks
stream_menu_extras\tExtras
stream_menu_vibration\tVibration
stream_menu_controls\tBildschirmsteuerung
editor_label_pass_through\tAn den Bildschirm durchreichen (halten und bewegen zum Umsehen)
stream_menu_layout\tLayout
stream_menu_exit\tStream beenden
stream_menu_layout_next_time\t&quot;%1$s&quot; wird beim nächsten Stream verwendet
dialog_title_save_changes\tÄnderungen speichern?
dialog_text_save_changes\tDie Änderungen an diesem Layout speichern?
menu_dont_save\tNicht speichern
dialog_title_save_layout_as\tLayout speichern unter
hint_layout_name_new\tName des neuen Layouts
toast_layout_saved\tLayout gespeichert
toast_layout_saved_as\tGespeichert als &quot;%s&quot;
toast_element_added\tHinzugefügt. Ziehe es an seinen Platz.
editor_section_element\tElement
editor_section_position\tPosition und Größe
editor_section_action\tAktion
editor_section_actions\tDieses Element
menu_share_layout\tLayout teilen
title_import_layout\tLayout importieren
import_failed\tDiese Datei konnte nicht gelesen werden
import_done\t&quot;%1$s&quot; importiert
import_done_rescaled\t&quot;%1$s&quot; importiert, an diesen Bildschirm angepasst
editor_label_name\tName
editor_label_x\tX
editor_label_y\tY
editor_label_width\tBreite
editor_label_height\tHöhe
editor_label_toggle\tUmschalter (tippen zum Drücken, erneut tippen zum Lösen)
editor_toggle_off_while_pass_through\tDer Umschalter ist aus, solange Durchreichen an ist
editor_label_sequence\tFolge von Ereignissen
editor_label_sequence_empty\tNoch keine Ereignisse. Füge unten einen Schritt hinzu.
editor_label_step_delay\tVerzögerung nach dem Schritt (ms)
editor_label_control_mode\tBetriebsart
editor_mode_axes\tAchsen
editor_mode_buttons\tTasten
editor_label_invert_y\tOben und unten umkehren (wenn das Spiel es andersherum liest)
editor_label_axis_x\tAchse X
editor_label_axis_y\tAchse Y
editor_label_direction_up\tTaste: oben
editor_label_direction_down\tTaste: unten
editor_label_direction_left\tTaste: links
editor_label_direction_right\tTaste: rechts
editor_add_step\tSchritt hinzufügen
editor_add_event\tEreignis hinzufügen
editor_remove_step\tSchritt entfernen
editor_step_title\tSchritt %d
editor_pick_device\tEreignisart
editor_device_keyboard\tTastatur
editor_device_mouse\tMaus
editor_device_gamepad\tGamepad
editor_pick_key\tTaste
editor_pick_button\tTaste
editor_remove_event\tEntfernen
editor_label_direction_action\tAktion für %s
category_about\tÜber die App
title_open_source_licenses\tOpen-Source-Lizenzen
summary_open_source_licenses\tDie Lizenz dieser App und die Lizenzen dessen, woraus sie gebaut ist
"""

TRANSLATIONS["values-fr"] = """
back\tRetour
title_universal_layouts\tDispositions à l'écran
title_add_layout\tAjouter une disposition
title_layout_image\tImage de la disposition
title_edit_controls\tModifier les commandes
title_reset_controls\tRéinitialiser
title_pick_image\tImage
title_use_default_image\tPar défaut
msg_layouts_help\tChoisissez la disposition utilisée pendant la diffusion. Touchez une disposition pour la modifier ou la supprimer ; la touche plus en ajoute une.
msg_no_layouts\tAucune disposition pour l'instant. Touchez la touche plus pour en créer une.
msg_layout_selected\tChoisie
msg_no_images\tAucune image trouvée sur cet appareil.
msg_image_permission_needed\tL'accès à vos images n'a pas été accordé, il n'y a donc rien à choisir. Accordez l'accès puis réessayez.
msg_image_picker_failed\tImpossible de charger les images de cet appareil.
dialog_title_layout_name\tNom de la disposition
hint_layout_name\tNom de la disposition
menu_edit_layout\tModifier
menu_choose_image\tChanger l'image
menu_use_layout\tUtiliser cette disposition
menu_delete_layout\tSupprimer
dialog_text_reset_layout_controls\tRéinitialiser la taille et la position des commandes de cette disposition ?
toast_layout_controls_reset\tCommandes réinitialisées
toast_layout_adjusted_to_fit\tCette disposition a été conçue pour un autre écran et a été adaptée. Utilisez « Réinitialiser » pour revenir à l'aspect d'origine.
title_layout_settings\tRéglages de la disposition
control_type_button\tBouton
control_type_stick\tJoystick
control_type_dpad\tCroix directionnelle
control_type_element\tÉlément
editor_label_center_button\tBouton du centre
editor_label_corner_radius\tArrondi des coins
editor_section_center_button\tCentre du joystick
editor_label_center_button_enabled\tAppuyer au centre agit comme un bouton
editor_label_center_button_action\tCe que le centre envoie
menu_add_button\tAjouter un bouton
menu_add_stick\tAjouter un joystick
menu_add_dpad\tAjouter une croix
menu_save\tEnregistrer
menu_save_as\tEnregistrer sous
menu_exit\tQuitter
menu_duplicate\tDupliquer
menu_copy_size\tCopier la taille
menu_paste_size\tColler la taille
menu_edit\tModifier
menu_delete\tSupprimer
dialog_title_delete_control\tSupprimer « %1$s » ?
stream_menu_title\tMenu de diffusion
stream_menu_keyboard\tAfficher le clavier
stream_menu_touch\tEntrée tactile
stream_menu_touch_off\tDésactivée
stream_menu_touch_trackpad\tPavé tactile
stream_menu_touch_right_stick\tJoystick droit
stream_menu_touch_left_stick\tJoystick gauche
stream_menu_touch_events\tTactile réel (expérimental)
stream_menu_opacity\tOpacité
stream_menu_sensitivity\tSensibilité
stream_menu_touchpad_mode\tMode du pavé tactile
stream_menu_touchpad_standard\tPavé tactile
stream_menu_touchpad_direct\tCurseur sous le doigt
stream_menu_touchpad_no_clicks\tSans clics
stream_menu_extras\tExtras
stream_menu_vibration\tVibration
stream_menu_controls\tCommandes à l'écran
editor_label_pass_through\tTransmettre au tactile (maintenir et glisser pour regarder autour)
stream_menu_layout\tDisposition
stream_menu_exit\tQuitter la diffusion
stream_menu_layout_next_time\t« %1$s » sera utilisée à la prochaine diffusion
dialog_title_save_changes\tEnregistrer les modifications ?
dialog_text_save_changes\tEnregistrer les modifications de cette disposition ?
menu_dont_save\tNe pas enregistrer
dialog_title_save_layout_as\tEnregistrer la disposition sous
hint_layout_name_new\tNom de la nouvelle disposition
toast_layout_saved\tDisposition enregistrée
toast_layout_saved_as\tEnregistrée sous &quot;%s&quot;
toast_element_added\tAjouté. Faites-le glisser à sa place.
editor_section_element\tÉlément
editor_section_position\tPosition et taille
editor_section_action\tAction
editor_section_actions\tCet élément
menu_share_layout\tPartager la disposition
title_import_layout\tImporter une disposition
import_failed\tImpossible de lire ce fichier
import_done\t« %1$s » importée
import_done_rescaled\t« %1$s » importée, adaptée à cet écran
editor_label_name\tNom
editor_label_x\tX
editor_label_y\tY
editor_label_width\tLargeur
editor_label_height\tHauteur
editor_label_toggle\tVerrou (toucher pour appuyer, toucher encore pour relâcher)
editor_toggle_off_while_pass_through\tLe verrou est désactivé tant que la transmission est active
editor_label_sequence\tSuite d'événements
editor_label_sequence_empty\tAucun événement. Ajoutez une étape ci-dessous.
editor_label_step_delay\tDélai après l'étape (ms)
editor_label_control_mode\tMode de fonctionnement
editor_mode_axes\tAxes
editor_mode_buttons\tBoutons
editor_label_invert_y\tInverser haut et bas (si le jeu le lit dans l'autre sens)
editor_label_axis_x\tAxe X
editor_label_axis_y\tAxe Y
editor_label_direction_up\tBouton : haut
editor_label_direction_down\tBouton : bas
editor_label_direction_left\tBouton : gauche
editor_label_direction_right\tBouton : droite
editor_add_step\tAjouter une étape
editor_add_event\tAjouter un événement
editor_remove_step\tSupprimer l'étape
editor_step_title\tÉtape %d
editor_pick_device\tType d'événement
editor_device_keyboard\tClavier
editor_device_mouse\tSouris
editor_device_gamepad\tManette
editor_pick_key\tTouche
editor_pick_button\tBouton
editor_remove_event\tRetirer
editor_label_direction_action\tAction pour %s
category_about\tÀ propos
title_open_source_licenses\tLicences open source
summary_open_source_licenses\tLa licence de cette application et celles de ce dont elle est construite
"""

TRANSLATIONS["values-es"] = """
back\tAtrás
title_universal_layouts\tDiseños en pantalla
title_add_layout\tAñadir diseño
title_layout_image\tImagen del diseño
title_edit_controls\tEditar controles
title_reset_controls\tRestablecer
title_pick_image\tImagen
title_use_default_image\tUsar predeterminada
msg_layouts_help\tElige el diseño que se usará al transmitir. Toca un diseño para editarlo o eliminarlo; el botón más añade uno nuevo.
msg_no_layouts\tAún no hay diseños. Toca el más para crear uno.
msg_layout_selected\tElegido
msg_no_images\tNo se han encontrado imágenes en este dispositivo.
msg_image_permission_needed\tNo se ha concedido acceso a tus imágenes, así que no hay nada que elegir. Concédelo y vuelve a intentarlo.
msg_image_picker_failed\tNo se han podido cargar las imágenes de este dispositivo.
dialog_title_layout_name\tNombre del diseño
hint_layout_name\tNombre del diseño
menu_edit_layout\tEditar
menu_choose_image\tCambiar imagen
menu_use_layout\tUsar este diseño
menu_delete_layout\tEliminar
dialog_text_reset_layout_controls\t¿Restablecer el tamaño y la posición de los controles de este diseño?
toast_layout_controls_reset\tControles restablecidos
toast_layout_adjusted_to_fit\tEste diseño se hizo para otro tamaño de pantalla y se ha adaptado a esta. Usa «Restablecer» para recuperar su aspecto original.
title_layout_settings\tAjustes del diseño
control_type_button\tBotón
control_type_stick\tStick
control_type_dpad\tCruceta
control_type_element\tElemento
editor_label_center_button\tBotón central
editor_label_corner_radius\tRadio de las esquinas
editor_section_center_button\tCentro del stick
editor_label_center_button_enabled\tTocar el centro actúa como un botón
editor_label_center_button_action\tQué envía el centro
menu_add_button\tAñadir botón
menu_add_stick\tAñadir stick
menu_add_dpad\tAñadir cruceta
menu_save\tGuardar
menu_save_as\tGuardar como
menu_exit\tSalir
menu_duplicate\tDuplicar
menu_copy_size\tCopiar tamaño
menu_paste_size\tPegar tamaño
menu_edit\tEditar
menu_delete\tEliminar
dialog_title_delete_control\t¿Eliminar «%1$s»?
stream_menu_title\tMenú de transmisión
stream_menu_keyboard\tMostrar teclado
stream_menu_touch\tEntrada táctil
stream_menu_touch_off\tDesactivada
stream_menu_touch_trackpad\tPanel táctil
stream_menu_touch_right_stick\tStick derecho
stream_menu_touch_left_stick\tStick izquierdo
stream_menu_touch_events\tTáctil real (experimental)
stream_menu_opacity\tOpacidad
stream_menu_sensitivity\tSensibilidad
stream_menu_touchpad_mode\tModo del panel táctil
stream_menu_touchpad_standard\tPanel táctil
stream_menu_touchpad_direct\tCursor bajo el dedo
stream_menu_touchpad_no_clicks\tSin clics
stream_menu_extras\tExtras
stream_menu_vibration\tVibración
stream_menu_controls\tControles en pantalla
editor_label_pass_through\tPasar al táctil (mantén y mueve para mirar alrededor)
stream_menu_layout\tDiseño
stream_menu_exit\tSalir de la transmisión
stream_menu_layout_next_time\t«%1$s» se usará la próxima vez que transmitas
dialog_title_save_changes\t¿Guardar los cambios?
dialog_text_save_changes\t¿Guardar los cambios de este diseño?
menu_dont_save\tNo guardar
dialog_title_save_layout_as\tGuardar diseño como
hint_layout_name_new\tNombre del nuevo diseño
toast_layout_saved\tDiseño guardado
toast_layout_saved_as\tGuardado como &quot;%s&quot;
toast_element_added\tAñadido. Arrástralo a su sitio.
editor_section_element\tElemento
editor_section_position\tPosición y tamaño
editor_section_action\tAcción
editor_section_actions\tEste elemento
menu_share_layout\tCompartir diseño
title_import_layout\tImportar diseño
import_failed\tNo se ha podido leer ese archivo
import_done\t«%1$s» importado
import_done_rescaled\t«%1$s» importado, adaptado a esta pantalla
editor_label_name\tNombre
editor_label_x\tX
editor_label_y\tY
editor_label_width\tAncho
editor_label_height\tAlto
editor_label_toggle\tBloqueo (toca para pulsar, toca otra vez para soltar)
editor_toggle_off_while_pass_through\tEl bloqueo está apagado mientras el paso al táctil está activo
editor_label_sequence\tSecuencia de eventos
editor_label_sequence_empty\tAún no hay eventos. Añade un paso abajo.
editor_label_step_delay\tRetardo tras el paso (ms)
editor_label_control_mode\tModo de funcionamiento
editor_mode_axes\tEjes
editor_mode_buttons\tBotones
editor_label_invert_y\tInvertir arriba y abajo (si el juego lo lee al revés)
editor_label_axis_x\tEje X
editor_label_axis_y\tEje Y
editor_label_direction_up\tBotón: arriba
editor_label_direction_down\tBotón: abajo
editor_label_direction_left\tBotón: izquierda
editor_label_direction_right\tBotón: derecha
editor_add_step\tAñadir paso
editor_add_event\tAñadir evento
editor_remove_step\tQuitar paso
editor_step_title\tPaso %d
editor_pick_device\tTipo de evento
editor_device_keyboard\tTeclado
editor_device_mouse\tRatón
editor_device_gamepad\tMando
editor_pick_key\tTecla
editor_pick_button\tBotón
editor_remove_event\tQuitar
editor_label_direction_action\tAcción para %s
category_about\tAcerca de
title_open_source_licenses\tLicencias de código abierto
summary_open_source_licenses\tLa licencia de esta aplicación y las de aquello con lo que está construida
"""

TRANSLATIONS["values-it"] = """
back\tIndietro
title_universal_layouts\tLayout sullo schermo
title_add_layout\tAggiungi layout
title_layout_image\tImmagine del layout
title_edit_controls\tModifica i controlli
title_reset_controls\tRipristina
title_pick_image\tImmagine
title_use_default_image\tUsa quella predefinita
msg_layouts_help\tScegli il layout usato durante lo streaming. Tocca un layout per modificarlo o eliminarlo; il più ne aggiunge uno nuovo.
msg_no_layouts\tNessun layout. Tocca il più per crearne uno.
msg_layout_selected\tScelto
msg_no_images\tNessuna immagine trovata su questo dispositivo.
msg_image_permission_needed\tL'accesso alle immagini non è stato concesso, quindi non c'è niente da scegliere. Concedilo e riprova.
msg_image_picker_failed\tImpossibile caricare le immagini da questo dispositivo.
dialog_title_layout_name\tNome del layout
hint_layout_name\tNome del layout
menu_edit_layout\tModifica
menu_choose_image\tCambia immagine
menu_use_layout\tUsa questo layout
menu_delete_layout\tElimina
dialog_text_reset_layout_controls\tRipristinare dimensione e posizione dei controlli di questo layout?
toast_layout_controls_reset\tControlli ripristinati
toast_layout_adjusted_to_fit\tQuesto layout era stato disposto per un altro schermo ed è stato adattato. Usa «Ripristina» per tornare all'aspetto originale.
title_layout_settings\tImpostazioni del layout
control_type_button\tPulsante
control_type_stick\tStick
control_type_dpad\tCroce direzionale
control_type_element\tElemento
editor_label_center_button\tPulsante centrale
editor_label_corner_radius\tRaggio degli angoli
editor_section_center_button\tCentro dello stick
editor_label_center_button_enabled\tToccare il centro funziona come un pulsante
editor_label_center_button_action\tCosa invia il centro
menu_add_button\tAggiungi pulsante
menu_add_stick\tAggiungi stick
menu_add_dpad\tAggiungi croce
menu_save\tSalva
menu_save_as\tSalva come
menu_exit\tEsci
menu_duplicate\tDuplica
menu_copy_size\tCopia dimensione
menu_paste_size\tIncolla dimensione
menu_edit\tModifica
menu_delete\tElimina
dialog_title_delete_control\tEliminare «%1$s»?
stream_menu_title\tMenu dello stream
stream_menu_keyboard\tMostra tastiera
stream_menu_touch\tInput tocco
stream_menu_touch_off\tDisattivato
stream_menu_touch_trackpad\tTouchpad
stream_menu_touch_right_stick\tStick destro
stream_menu_touch_left_stick\tStick sinistro
stream_menu_touch_events\tTocco reale (sperimentale)
stream_menu_opacity\tOpacità
stream_menu_sensitivity\tSensibilità
stream_menu_touchpad_mode\tModo del touchpad
stream_menu_touchpad_standard\tTouchpad
stream_menu_touchpad_direct\tCursore sotto il dito
stream_menu_touchpad_no_clicks\tSenza clic
stream_menu_extras\tExtra
stream_menu_vibration\tVibrazione
stream_menu_controls\tControlli sullo schermo
editor_label_pass_through\tPassa al tocco (tieni e muovi per guardarti intorno)
stream_menu_layout\tLayout
stream_menu_exit\tEsci dallo stream
stream_menu_layout_next_time\t«%1$s» sarà usato al prossimo streaming
dialog_title_save_changes\tSalvare le modifiche?
dialog_text_save_changes\tSalvare le modifiche a questo layout?
menu_dont_save\tNon salvare
dialog_title_save_layout_as\tSalva layout come
hint_layout_name_new\tNome del nuovo layout
toast_layout_saved\tLayout salvato
toast_layout_saved_as\tSalvato come &quot;%s&quot;
toast_element_added\tAggiunto. Trascinalo al suo posto.
editor_section_element\tElemento
editor_section_position\tPosizione e dimensione
editor_section_action\tAzione
editor_section_actions\tQuesto elemento
menu_share_layout\tCondividi layout
title_import_layout\tImporta layout
import_failed\tImpossibile leggere quel file
import_done\t«%1$s» importato
import_done_rescaled\t«%1$s» importato, adattato a questo schermo
editor_label_name\tNome
editor_label_x\tX
editor_label_y\tY
editor_label_width\tLarghezza
editor_label_height\tAltezza
editor_label_toggle\tBlocco (tocca per premere, tocca ancora per rilasciare)
editor_toggle_off_while_pass_through\tIl blocco è spento finché il passaggio al tocco è attivo
editor_label_sequence\tSequenza di eventi
editor_label_sequence_empty\tNessun evento. Aggiungi un passo qui sotto.
editor_label_step_delay\tRitardo dopo il passo (ms)
editor_label_control_mode\tModalità di funzionamento
editor_mode_axes\tAssi
editor_mode_buttons\tPulsanti
editor_label_invert_y\tInverti alto e basso (se il gioco lo legge al contrario)
editor_label_axis_x\tAsse X
editor_label_axis_y\tAsse Y
editor_label_direction_up\tPulsante: su
editor_label_direction_down\tPulsante: giù
editor_label_direction_left\tPulsante: sinistra
editor_label_direction_right\tPulsante: destra
editor_add_step\tAggiungi passo
editor_add_event\tAggiungi evento
editor_remove_step\tRimuovi passo
editor_step_title\tPasso %d
editor_pick_device\tTipo di evento
editor_device_keyboard\tTastiera
editor_device_mouse\tMouse
editor_device_gamepad\tController
editor_pick_key\tTasto
editor_pick_button\tPulsante
editor_remove_event\tRimuovi
editor_label_direction_action\tAzione per %s
category_about\tInformazioni
title_open_source_licenses\tLicenze open source
summary_open_source_licenses\tLa licenza di questa app e quelle di ciò con cui è costruita
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
