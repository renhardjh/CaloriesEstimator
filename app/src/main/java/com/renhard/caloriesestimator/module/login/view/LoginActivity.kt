package com.renhard.caloriesestimator.module.login.view

import android.content.Context
import android.content.Intent
import android.os.Bundle
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import com.renhard.caloriesestimator.R
import com.renhard.caloriesestimator.databinding.ActivityLoginBinding
import com.renhard.caloriesestimator.module.main.view.MainActivity

class LoginActivity: AppCompatActivity() {
    private lateinit var binding: ActivityLoginBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        binding = ActivityLoginBinding.inflate(layoutInflater)

        enableEdgeToEdge()

        setContentView(binding.root)

        val sharedPref = getPreferences(Context.MODE_PRIVATE)
        val email = sharedPref.getString("email", null)
        val pswd = sharedPref.getString("password", null)
        email?.let {
            binding.etEmail.setText(it)
        }
        pswd?.let {
            binding.etPswd.setText(it)
            binding.cbRemember.isChecked = true
        }

        binding.btnSignIn.setOnClickListener {
            if(binding.etEmail.text.toString() == "renhard.joki@gmail.com"
                && binding.etPswd.text.toString() == "111111") {
                finish()
                val intentMainActivity = Intent(this, MainActivity::class.java)
                startActivity(intentMainActivity)

                val sharedPref = getPreferences(Context.MODE_PRIVATE)
                val isRemember = binding.cbRemember.isChecked
                with (sharedPref.edit()) {
                    putString("email", if (isRemember) binding.etEmail.text.toString() else null)
                    putString("password", if (isRemember) binding.etPswd.text.toString() else null)
                    apply()
                }
            } else {
                binding.etPswd.error = getString(R.string.email_atau_password_salah)
            }
        }
    }
}