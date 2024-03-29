package com.coinz.lw.coinz

import android.animation.Animator
import android.animation.AnimatorListenerAdapter
import android.annotation.TargetApi
import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.support.v7.app.AppCompatActivity
import android.text.TextUtils
import android.util.Log
import android.view.View
import android.view.inputmethod.EditorInfo
import android.widget.TextView
import com.coinz.lw.coinz.Constants.Companion.BANK_COINS
import com.coinz.lw.coinz.Constants.Companion.USER
import com.coinz.lw.coinz.Constants.Companion.WALLET_COINS
import com.coinz.lw.coinz.Constants.Companion.getBankCoinsRef
import com.coinz.lw.coinz.Constants.Companion.getWalletCoinsRef
import com.google.android.gms.tasks.Tasks
import com.google.firebase.auth.*
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.FirebaseFirestoreSettings
import kotlinx.android.synthetic.main.activity_login.*
import org.jetbrains.anko.doAsync
import org.jetbrains.anko.longToast
import org.jetbrains.anko.sdk25.coroutines.onClick
import org.jetbrains.anko.toast
import org.jetbrains.anko.uiThread



/**
 * A login screen that offers login via email/password.
 */
class LoginActivity : AppCompatActivity() {
    /**
     * Keep track of the login task to ensure we can cancel it if requested.
     */
    private val baseTag = "LOGIN_ACTIVITY"

    private lateinit var mAuth: FirebaseAuth
    private lateinit var db: FirebaseFirestore


    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_login)

        mAuth = FirebaseAuth.getInstance()
        db = FirebaseFirestore.getInstance()
//        db.document("Users/a@a.de").delete()
        // Ensure that deleted documents don't stay in cache -
        val settings = FirebaseFirestoreSettings.Builder()
                .setPersistenceEnabled(false)
                .build()
        db.firestoreSettings = settings

        // Set up the login form.
        password.setOnEditorActionListener(TextView.OnEditorActionListener { _, id, _ ->
            if (id == EditorInfo.IME_ACTION_DONE || id == EditorInfo.IME_NULL) {
                attemptSignUp()
                return@OnEditorActionListener true
            }
            false
        })

        email_sign_up_button.setOnClickListener { attemptSignUp() }
        email_login_button.onClick { attemptLogIn() }

    }

    // Creates a new user in the db
    private fun createUser(emailStr: String) {
        val tag = "$baseTag [createUser]"
        // neither user nor user.email can be zero in this code block
        val newUser = UserModel(emailStr)

        // Initialise USER to new user - COINS will be an empty list so we don't need to change it
        USER = newUser
        doAsync {
            try {
                val newUserRef = db.collection("Users").document(newUser.email)
                Tasks.await(newUserRef.set(newUser))
                Log.d(tag, "Successfully created User in db: $newUser")

                // Don't need to fetch User since already set USER above
                uiThread { updateUI(false) }
            } catch (e: Exception) {
                Log.d(tag, "There was a problem when creating the user. $e")
                showProgress(false)
                uiThread { toast("There was a problem creating the user. Please try again or try to restart the app") }
            }
        }
    }

    /**
     * Attempts to sign in or register the account specified by the login form.
     * If there are form errors (invalid email, missing fields, etc.), the
     * errors are presented and no actual login attempt is made.
     */
    private fun attemptSignUp() {
        val tag = "$baseTag [attemptSignUp]"

        // Reset errors.
        email.error = null
        password.error = null

        // Store values at the time of the login attempt.
        val emailStr = email.text.toString()
        val passwordStr = password.text.toString()

        var cancel = false
        var focusView: View? = null

        // Check for a valid password, if the user entered one.
        if (TextUtils.isEmpty(passwordStr)) {
            password.error = getString(R.string.error_field_required)
            focusView = password
            cancel = true
        } else if (!isPasswordValid(passwordStr)) {
            password.error = getString(R.string.error_invalid_password)
            focusView = password
            cancel = true
        }

        // Check for a valid email address.
        if (TextUtils.isEmpty(emailStr)) {
            email.error = getString(R.string.error_field_required)
            focusView = email
            cancel = true
        } else if (!isEmailValid(emailStr)) {
            email.error = getString(R.string.error_invalid_email)
            focusView = email
            cancel = true
        }

        if (cancel) {
            // There was an error; don't attempt login and focus the first
            // form field with an error.
            focusView?.requestFocus()
        } else {
            // Show a progress spinner, and kick off a background task to
            // perform the user login attempt.
            showProgress(true)

            mAuth.createUserWithEmailAndPassword(emailStr, passwordStr).addOnCompleteListener(this) { task ->
                if (task.isSuccessful) {
                    createUser(emailStr)
                } else {
                    // Reset errors
                    email.error = null
                    password.error = null

                    val err = task.exception
                    when (err) {
                        // This has to be checked first since FirebaseAuthWeakPasswordException extends FirebaseAuthInvalidCredentialsException
                        is FirebaseAuthWeakPasswordException -> {
                            password.error = err.reason // the reason why the password is too weak
                        }
                        is FirebaseAuthInvalidCredentialsException -> {
                            email.error = getString(R.string.error_invalid_email)
                            focusView = email
                        }
                        is FirebaseAuthUserCollisionException -> {
                            email.error = getString(R.string.error_email_duplicate)
                            focusView = email
                        }
                        else -> longToast("There was a problem. Please Try again")
                    }
                    showProgress(false)
                    focusView?.requestFocus()
                    Log.d(tag, "SignUp Failed: ${task.exception}")
                }
            }
        }
    }

    private fun attemptLogIn() {
        val tag = "$baseTag [attemptLogin]"

        // Reset errors.
        email.error = null
        password.error = null

        var focusView: View? = null

        // Store values at the time of the login attempt.
        val emailStr = email.text.toString()
        val passwordStr = password.text.toString()

        showProgress(true)
        mAuth.signInWithEmailAndPassword(emailStr, passwordStr).addOnCompleteListener(this) { task ->
            if (task.isSuccessful) {
                Log.d(tag, "Successfully logged in user with email: $emailStr")
                fetchUser(mAuth.currentUser, true)
            } else {
                val err = task.exception
                when (err) {
                    is FirebaseAuthInvalidCredentialsException -> {
                        password.error = getString(R.string.error_incorrect_password)
                        focusView = password
                    }
                    is FirebaseAuthInvalidUserException -> {
                        email.error = getString(R.string.error_email_non_existent)
                        focusView = email
                    }
                    else -> longToast("There was a problem. Please Try again")
                }
            }
            showProgress(false)
            focusView?.requestFocus()
            Log.d(tag, "Login Failed: ${task.exception}")

        }
    }

    // Very basic evaluation - more sophisticated one on server side
    private fun isEmailValid(email: String): Boolean {
        return email.contains("@") && email.contains(".")
    }

    // Very basic evaluation - more sophisticated one on server side
    private fun isPasswordValid(password: String): Boolean {
        return password.length > 5
    }

    /**
     * Shows the progress UI and hides the login form.
     */
    @TargetApi(Build.VERSION_CODES.HONEYCOMB_MR2)
    private fun showProgress(show: Boolean) {
        // On Honeycomb MR2 we have the ViewPropertyAnimator APIs, which allow
        // for very easy animations. If available, use these APIs to fade-in
        // the progress spinner.
        // The minimum SDK version for this app ensures that the ViewPropertyAnimator is available

        val shortAnimTime = resources.getInteger(android.R.integer.config_shortAnimTime).toLong()

        login_form.visibility = if (show) View.GONE else View.VISIBLE
        login_form.animate()
                .setDuration(shortAnimTime)
                .alpha((if (show) 0 else 1).toFloat())
                .setListener(object : AnimatorListenerAdapter() {
                    override fun onAnimationEnd(animation: Animator) {
                        login_form.visibility = if (show) View.GONE else View.VISIBLE
                    }
                })

        login_progress.visibility = if (show) View.VISIBLE else View.GONE
        login_progress.animate()
                .setDuration(shortAnimTime)
                .alpha((if (show) 1 else 0).toFloat())
                .setListener(object : AnimatorListenerAdapter() {
                    override fun onAnimationEnd(animation: Animator) {
                        login_progress.visibility = if (show) View.VISIBLE else View.GONE
                    }
                })
    }

    // Updates the UI by switching to MapActivity and displaying a welcome message - if user != null
    private fun updateUI(alreadyRegistered: Boolean) {
        val tag = "$baseTag [updateUI]"

        // Select correct message to display for new/already registered user
        val toastMessage = if (alreadyRegistered) getString(R.string.message_welcome_back) else
            getString(R.string.message_welcome)
        toast(toastMessage)

        // Switch to MapActivity
        val openMapIntent = Intent(this, MapActivity::class.java)
        startActivity(openMapIntent)
        Log.d(tag, "Switch from Login- to MapActivity")
    }

    // Fetches User data from db and then calls updateUI()
    private fun fetchUser(user: FirebaseUser?, alreadyRegistered: Boolean) {
        val tag = "$baseTag [fetchUser]"
        if (user != null) {
            showProgress(true)
            doAsync {
                try {
                    USER = Tasks.await(db.document("Users/${user.email}").get()).toObject(UserModel::class.java) ?: throw Exception("Couldn't retrieve user data from db.")
                    fetchCoins()
                    uiThread { updateUI(alreadyRegistered) }
                } catch (e: Exception) {
                    Log.d(tag, "Problem during the SignUp. $e ${e.cause}")
                    uiThread {
                        longToast("There was a problem. Please try again.")
                        showProgress(false)
                    }
                    e.printStackTrace()
                }
            }
        }
    }

    // Fetches Coins from bank (BANK and WALLET) - NEEDS to be called in asynchronous call - NOT Main Thread
    private fun fetchCoins() {
        val walletCoinsQuery= Tasks.await(getWalletCoinsRef()?.get()!!)
        for (docSnap: DocumentSnapshot in walletCoinsQuery) {
            val coin = docSnap.toObject(CoinModel::class.java)
            if(coin != null) {
                WALLET_COINS.add(coin)
            }
        }
        val bankCoinsQuery= Tasks.await(getBankCoinsRef()?.get()!!)
        for (docSnap: DocumentSnapshot in bankCoinsQuery) {
            val coin = docSnap.toObject(CoinModel::class.java)
            if(coin != null) {
                BANK_COINS.add(coin)
            }
        }
    }
    override fun onStart() {
        super.onStart()
        //Fetch user data from db - which then calls updateUI()
        fetchUser(mAuth.currentUser, true)
    }
}
