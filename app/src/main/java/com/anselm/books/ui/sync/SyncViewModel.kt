package com.anselm.books.ui.sync

import android.accounts.Account
import androidx.lifecycle.ViewModel

class SyncViewModel(
    var account: Account? = null
): ViewModel()