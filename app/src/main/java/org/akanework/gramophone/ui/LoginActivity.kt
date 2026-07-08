package org.akanework.gramophone.ui

import android.content.Intent
import android.os.Bundle
import android.widget.Button
import android.widget.EditText
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import org.akanework.gramophone.R
import org.akanework.gramophone.logic.api.AuthManager
import org.akanework.gramophone.logic.api.GramophoneApi
import org.akanework.gramophone.logic.api.LoginResponse
import retrofit2.Call
import retrofit2.Callback
import retrofit2.Response
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import android.content.Context

class LoginActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Проверка: Если токен уже есть, сразу идем в Main
        if (AuthManager.getToken(this) != null) {
            startMainActivity()
            return
        }

        setContentView(R.layout.activity_login)

        val etUsername = findViewById<EditText>(R.id.etUsername)
        val etPassword = findViewById<EditText>(R.id.etPassword)
        val btnLogin = findViewById<Button>(R.id.btnLogin)
        val tvStatus = findViewById<TextView>(R.id.tvStatus)

        // Настройка Retrofit (Вставь СВОЙ IP адрес!)
        val retrofit = Retrofit.Builder()
            .baseUrl("http://185.196.41.31/") // <--- ТВОЙ IP
            .addConverterFactory(GsonConverterFactory.create())
            .build()

        val api = retrofit.create(GramophoneApi::class.java)

        btnLogin.setOnClickListener {
            val user = etUsername.text.toString()
            val pass = etPassword.text.toString()

            if (user.isEmpty() || pass.isEmpty()) return@setOnClickListener

            btnLogin.isEnabled = false
            tvStatus.text = "Signing in..."

            // Отправляем запрос
            api.login(user, pass).enqueue(object : Callback<LoginResponse> {
                override fun onResponse(call: Call<LoginResponse>, response: Response<LoginResponse>) {
                    btnLogin.isEnabled = true
                    if (response.isSuccessful && response.body() != null) {
                        val token = response.body()!!.access_token
                        AuthManager.saveToken(this@LoginActivity, token)

                        // 🔥 ДОБАВЛЯЕМ СОХРАНЕНИЕ НИКНЕЙМА
                        val prefs = getSharedPreferences("auth_prefs", Context.MODE_PRIVATE)
                        prefs.edit().putString("username", user).apply()

                        startMainActivity()
                    } else {
                        tvStatus.text = "Error: ${response.code()} ${response.message()}"
                    }
                }

                override fun onFailure(call: Call<LoginResponse>, t: Throwable) {
                    btnLogin.isEnabled = true
                    tvStatus.text = "Connection failed: ${t.message}"
                }
            })
        }
    }

    private fun startMainActivity() {
        val intent = Intent(this, MainActivity::class.java)
        startActivity(intent)
        finish() // Закрываем экран логина, чтобы нельзя было вернуться назад
    }
}