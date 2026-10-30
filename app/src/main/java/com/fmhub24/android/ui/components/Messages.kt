package com.fmhub24.android.ui.components

import androidx.compose.runtime.Composable
import com.fmhub24.android.R
import com.fmhub24.android.data.model.DataException

/**
 * Turns a read failure into the exact sentence the user is told (spec section 8): every kind of
 * failure has its own honest message and none of them is hidden behind a generic spinner.
 */
@Composable
fun dataErrorMessage(error: DataException?): String = when (error) {
    null -> stringRes(R.string.state_load_failed)
    is DataException.NotConfigured -> stringRes(R.string.state_not_configured)
    is DataException.PermissionBlocked -> stringRes(R.string.state_rls_blocked)
    is DataException.AuthFailed -> stringRes(R.string.state_auth_failed)
    is DataException.Offline -> stringRes(R.string.state_network_error)
    is DataException.Http -> if (error.code in 500..599) {
        stringRes(R.string.state_maintenance)
    } else {
        stringRes(R.string.state_load_failed)
    }
    is DataException.Malformed -> stringRes(R.string.state_load_failed)
}
