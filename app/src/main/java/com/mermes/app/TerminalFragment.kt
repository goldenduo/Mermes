package com.mermes.app

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.fragment.app.Fragment
import com.mermes.core.terminal.TerminalManager
import com.mermes.core.terminal.TerminalSession
import com.mermes.core.terminal.TerminalSessionCallback
import com.mermes.core.terminal.view.TerminalView

class TerminalFragment : Fragment() {

    companion object {
        private const val ARG_FAILSAFE = "failsafe"

        fun newInstance(failsafe: Boolean = false): TerminalFragment {
            return TerminalFragment().apply {
                arguments = Bundle().apply {
                    putBoolean(ARG_FAILSAFE, failsafe)
                }
            }
        }
    }

    private var terminalView: TerminalView? = null
    private var isFailsafe = false

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        return inflater.inflate(R.layout.fragment_terminal, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        terminalView = view.findViewById(R.id.terminalView)
        isFailsafe = arguments?.getBoolean(ARG_FAILSAFE, false) ?: false

        startSession()
    }

    private fun startSession() {
        val tv = terminalView ?: return
        val ctx = context ?: return

        if (isFailsafe) {
            Toast.makeText(ctx, getString(R.string.failsafe_notice), Toast.LENGTH_LONG).show()
            tv.startFailsafeSession()
        } else {
            tv.startShellSession()
        }
        tv.showKeyboard()
    }

    fun onBackPressed(): Boolean {
        // Send ESC to terminal
        val session = terminalView?.getSession() ?: return false
        TerminalManager.writeToSession(session, "".toByteArray())
        return true
    }

    override fun onDestroyView() {
        terminalView?.detachSession()
        terminalView = null
        super.onDestroyView()
    }
}
