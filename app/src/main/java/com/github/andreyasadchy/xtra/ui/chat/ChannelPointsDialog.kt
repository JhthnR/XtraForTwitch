package com.github.andreyasadchy.xtra.ui.chat

import android.app.Dialog
import android.content.DialogInterface
import android.os.Bundle
import android.view.View
import android.view.WindowManager
import android.widget.LinearLayout
import androidx.appcompat.app.AlertDialog
import androidx.core.view.isVisible
import androidx.core.widget.addTextChangedListener
import androidx.fragment.app.DialogFragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import com.github.andreyasadchy.xtra.R
import com.github.andreyasadchy.xtra.databinding.DialogChannelPointRewardsBinding
import com.github.andreyasadchy.xtra.model.ui.ChannelPointRewardCatalog
import com.github.andreyasadchy.xtra.util.getAlertDialogBuilder
import com.google.android.material.button.MaterialButton
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch
import java.text.NumberFormat
import javax.inject.Inject
import kotlin.math.roundToInt

/** Focused custom-reward flow adapted from thiscallnet/Xtra fbffe56. */
@AndroidEntryPoint
class ChannelPointsDialog : DialogFragment() {
    @Inject lateinit var sessions: ChannelPointRewardsSessionProvider
    private val viewModel: ChannelPointRewardsViewModel by viewModels({ requireParentFragment() })
    private var binding: DialogChannelPointRewardsBinding? = null
    private var renderedCatalog: ChannelPointRewardCatalog? = null
    private var renderedBlockedIds: Set<String> = emptySet()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // A dialog-only fragment has no Fragment view lifecycle owner. Install one
        // fragment-owned collector; each STARTED interval renders the current binding.
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.state.collect { render(it) }
            }
        }
    }

    override fun onCreateDialog(savedInstanceState: Bundle?): Dialog {
        val content = DialogChannelPointRewardsBinding.inflate(layoutInflater)
        binding = content
        content.input.addTextChangedListener { viewModel.inputChanged(it?.toString().orEmpty()) }
        return requireContext().getAlertDialogBuilder()
            .setTitle(R.string.channel_rewards_title)
            .setView(content.root)
            .setPositiveButton(R.string.channel_rewards_redeem, null)
            .setNeutralButton(R.string.channel_rewards_refresh, null)
            .setNegativeButton(R.string.close, null)
            .create()
    }

    override fun onStart() {
        super.onStart()
        val alert = dialog as AlertDialog
        alert.window?.let {
            val metrics = resources.displayMetrics
            it.setLayout((metrics.widthPixels * .94f).roundToInt().coerceAtMost((640 * metrics.density).roundToInt()), (metrics.heightPixels * .8f).roundToInt())
            it.setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE)
        }
        alert.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener { viewModel.confirm(session()) }
        alert.getButton(AlertDialog.BUTTON_NEUTRAL).setOnClickListener { viewModel.refresh(session()) }
        alert.getButton(AlertDialog.BUTTON_NEGATIVE).setOnClickListener {
            if (viewModel.state.value.selected != null && !viewModel.state.value.sending) viewModel.cancelSelection() else dismiss()
        }
        viewModel.open(session())
    }

    private fun session() = sessions.current(requireArguments().getString(CHANNEL_ID), requireArguments().getString(CHANNEL_LOGIN))

    private fun render(state: ChannelPointRewardsState) {
        val binding = binding ?: return
        val alert = dialog as? AlertDialog ?: return
        val number = NumberFormat.getIntegerInstance()
        val catalog = state.catalog
        binding.balance.text = state.catalog?.let { getString(R.string.channel_rewards_balance, number.format(it.balance)) }.orEmpty()
        binding.balance.isVisible = state.catalog != null
        binding.progress.isVisible = state.busy
        val status = when (state.notice) {
            RewardNotice.LOGIN -> R.string.channel_rewards_login
            RewardNotice.LOAD_FAILED -> R.string.channel_rewards_load_failed
            RewardNotice.UNAVAILABLE -> R.string.channel_rewards_unavailable
            RewardNotice.INPUT_REQUIRED -> R.string.channel_rewards_input_required
            RewardNotice.INSUFFICIENT -> R.string.channel_rewards_insufficient
            RewardNotice.CHANGED -> R.string.channel_rewards_changed
            RewardNotice.REJECTED -> R.string.channel_rewards_rejected
            RewardNotice.UNKNOWN -> R.string.channel_rewards_unknown
            RewardNotice.SUCCESS -> R.string.channel_rewards_success
            null -> when {
                state.sending -> R.string.channel_rewards_sending
                state.busy -> R.string.channel_rewards_loading
                state.catalog?.rewards?.isEmpty() == true -> R.string.channel_rewards_empty
                else -> null
            }
        }
        binding.status.isVisible = status != null
        binding.status.text = status?.let { getString(it) }.orEmpty()
        val reward = state.selected
        binding.details.isVisible = reward != null
        binding.rewards.isVisible = reward == null
        if (reward != null) {
            binding.summary.text = listOfNotNull(
                getString(R.string.channel_rewards_confirm, number.format(reward.cost), reward.title),
                reward.prompt?.takeIf { it.isNotBlank() },
            ).joinToString("\n\n")
            binding.inputLayout.isVisible = reward.requiresInput
            binding.input.isEnabled = !state.busy
            if (binding.input.text?.toString() != state.input) binding.input.setText(state.input)
            binding.inputLayout.error = if (state.notice == RewardNotice.INPUT_REQUIRED) getString(R.string.channel_rewards_input_required) else null
        }
        if (renderedCatalog != state.catalog || renderedBlockedIds != state.blockedRewardIds) {
            renderedCatalog = state.catalog
            renderedBlockedIds = state.blockedRewardIds
            binding.rewards.removeAllViews()
            catalog?.rewards?.forEach { option ->
                val disabledReason = when {
                    option.id in state.blockedRewardIds -> getString(R.string.channel_rewards_check_twitch)
                    !option.available -> getString(R.string.channel_rewards_unavailable)
                    option.cost > catalog.balance -> getString(R.string.channel_rewards_insufficient)
                    else -> null
                }
                val button = MaterialButton(requireContext()).apply {
                    text = getString(R.string.channel_rewards_option, option.title, number.format(option.cost)) + disabledReason?.let { "\n$it" }.orEmpty()
                    minHeight = (48 * resources.displayMetrics.density).roundToInt()
                    isAllCaps = false
                    isEnabled = disabledReason == null && !state.busy
                    setOnClickListener { viewModel.select(option) }
                }
                binding.rewards.addView(button, LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT))
            }
        }
        for (index in 0 until binding.rewards.childCount) {
            val currentCatalog = catalog ?: continue
            val option = currentCatalog.rewards.getOrNull(index) ?: continue
            binding.rewards.getChildAt(index).isEnabled = !state.busy && option.available && option.cost <= currentCatalog.balance && option.id !in state.blockedRewardIds
        }
        alert.getButton(AlertDialog.BUTTON_POSITIVE).apply {
            isVisible = reward != null
            text = reward?.let { getString(R.string.channel_rewards_spend, number.format(it.cost)) }
            isEnabled = !state.busy && reward != null && (!reward.requiresInput || state.input.isNotBlank())
        }
        alert.getButton(AlertDialog.BUTTON_NEUTRAL).apply {
            isVisible = reward == null
            isEnabled = !state.busy
        }
        alert.getButton(AlertDialog.BUTTON_NEGATIVE).setText(if (reward != null && !state.sending) android.R.string.cancel else R.string.close)
    }

    override fun onDismiss(dialog: DialogInterface) {
        if (activity?.isChangingConfigurations != true) viewModel.cancelSelection()
        super.onDismiss(dialog)
    }

    override fun onDestroyView() {
        binding = null
        renderedCatalog = null
        renderedBlockedIds = emptySet()
        super.onDestroyView()
    }

    companion object {
        const val TAG = "channelPointRewards"
        private const val CHANNEL_ID = "rewardChannelId"
        private const val CHANNEL_LOGIN = "rewardChannelLogin"
        fun newInstance(channelId: String, channelLogin: String) = ChannelPointsDialog().apply {
            arguments = Bundle().apply { putString(CHANNEL_ID, channelId); putString(CHANNEL_LOGIN, channelLogin) }
        }
    }
}
