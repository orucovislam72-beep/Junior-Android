package com.riglineaimstriker.junior.ui.components

import androidx.annotation.DrawableRes
import com.riglineaimstriker.junior.R

/**
 * Единая точка доступа к оригинальным контурным иконкам Junior (res/drawable/ic_*.xml).
 * Рисуются только через [JuniorIcon]. Каждой иконке соответствует ровно один файл.
 */
object JuniorIcons {
    @DrawableRes val Chat: Int = R.drawable.ic_chat
    @DrawableRes val NewChat: Int = R.drawable.ic_new_chat
    @DrawableRes val Send: Int = R.drawable.ic_send
    @DrawableRes val Stop: Int = R.drawable.ic_stop
    @DrawableRes val Mic: Int = R.drawable.ic_mic
    @DrawableRes val Attach: Int = R.drawable.ic_attach
    @DrawableRes val Copy: Int = R.drawable.ic_copy
    @DrawableRes val Share: Int = R.drawable.ic_share
    @DrawableRes val Edit: Int = R.drawable.ic_edit
    @DrawableRes val Repeat: Int = R.drawable.ic_repeat
    @DrawableRes val Delete: Int = R.drawable.ic_delete
    @DrawableRes val Search: Int = R.drawable.ic_search
    @DrawableRes val Folder: Int = R.drawable.ic_folder
    @DrawableRes val File: Int = R.drawable.ic_file
    @DrawableRes val Settings: Int = R.drawable.ic_settings
    @DrawableRes val Skills: Int = R.drawable.ic_skills
    @DrawableRes val Agent: Int = R.drawable.ic_agent
    @DrawableRes val Check: Int = R.drawable.ic_check
    @DrawableRes val Close: Int = R.drawable.ic_close
    @DrawableRes val Back: Int = R.drawable.ic_back
    @DrawableRes val More: Int = R.drawable.ic_more
    @DrawableRes val Warning: Int = R.drawable.ic_warning
    @DrawableRes val Download: Int = R.drawable.ic_download
    @DrawableRes val ChevronDown: Int = R.drawable.ic_chevron_down
    @DrawableRes val ChevronRight: Int = R.drawable.ic_chevron_right

    /** Псевдоним для совместимости со старым именем. */
    @DrawableRes val AttachFile: Int = Attach
}
