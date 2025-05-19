//botBridge.java

package com.example.llamachat;

import java.util.Arrays;

import okhttp3.Interceptor;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import retrofit2.*;
import retrofit2.converter.gson.GsonConverterFactory;

public class BotBridge {

    private static final String AUTH_KEY = "1ea775a7-4e7d-47bf-b646-f18e28075d58";

    private static final TalkService talkService;

    static {
        OkHttpClient okClient = new OkHttpClient.Builder()
                .addInterceptor((Interceptor.Chain chain) -> {
                    Request base = chain.request();
                    Request decorated = base.newBuilder()
                            .addHeader("Authorization", "Bearer " + AUTH_KEY)
                            .addHeader("Content-Type", "application/json")
                            .method(base.method(), base.body())
                            .build();
                    return chain.proceed(decorated);
                })
                .build();

        Retrofit retro = new Retrofit.Builder()
                .baseUrl("https://api.llama-api.com/")
                .client(okClient)
                .addConverterFactory(GsonConverterFactory.create())
                .build();

        talkService = retro.create(TalkService.class);
    }

    public static void askBot(String query, BotReply callback) {
        ChatPrompt.Message sys = new ChatPrompt.Message("system", "You are a mobile assistant.");
        ChatPrompt.Message usr = new ChatPrompt.Message("user", query);

        ChatPrompt payload = new ChatPrompt("llama3.1-70b", Arrays.asList(sys, usr), false);

        talkService.getReply(payload).enqueue(new Callback<ChatResponse>() {
            @Override
            public void onResponse(Call<ChatResponse> call, Response<ChatResponse> response) {
                if (response.isSuccessful() && response.body() != null) {
                    callback.onReply(response.body().getAnswer());
                } else {
                    callback.onReply("❌ Bot failed.");
                }
            }

            @Override
            public void onFailure(Call<ChatResponse> call, Throwable t) {
                callback.onReply("❌ No connection.");
            }
        });
    }

    public interface BotReply {
        void onReply(String replyText);
    }
}

//chatAdapter.java

package com.example.llamachat;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import android.view.*;
import android.widget.TextView;

import java.util.List;

public class ChatAdapter extends RecyclerView.Adapter<RecyclerView.ViewHolder> {

    private final List<ChatItem> chatItems;
    private final String user;

    public ChatAdapter(List<ChatItem> chatItems, String user) {
        this.chatItems = chatItems;
        this.user = user;
    }

    private static final int TYPE_USER = 0;
    private static final int TYPE_BOT = 1;

    @Override
    public int getItemViewType(int position) {
        return chatItems.get(position).getSender().equals("user") ? TYPE_USER : TYPE_BOT;
    }

    @NonNull
    @Override
    public RecyclerView.ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        int layout = viewType == TYPE_USER ? R.layout.item_user_msg : R.layout.item_bot_msg;
        View view = LayoutInflater.from(parent.getContext()).inflate(layout, parent, false);
        return new ChatHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull RecyclerView.ViewHolder holder, int position) {
        ((ChatHolder) holder).bind(chatItems.get(position));
    }

    @Override
    public int getItemCount() {
        return chatItems.size();
    }

    static class ChatHolder extends RecyclerView.ViewHolder {
        private final TextView msgText;

        public ChatHolder(View itemView) {
            super(itemView);
            msgText = itemView.findViewById(R.id.messageText);
        }

        void bind(ChatItem item) {
            msgText.setText(item.getContent());
        }
    }
}
//chatitem.java

package com.example.llamachat;

public class ChatItem {
    private String content;
    private String sender;  // "user" or "bot"

    public ChatItem(String content, String sender) {
        this.content = content;
        this.sender = sender;
    }

    public String getContent() {
        return content;
    }

    public String getSender() {
        return sender;
    }
}
//chatprompt.java

package com.example.llamachat;

import java.util.List;

public class ChatPrompt {
    public String model;
    public List<Message> messages;
    public boolean stream;

    public ChatPrompt(String model, List<Message> messages, boolean stream) {
        this.model = model;
        this.messages = messages;
        this.stream = stream;
    }

    public static class Message {
        public String role;
        public String content;

        public Message(String role, String content) {
            this.role = role;
            this.content = content;
        }
    }
}


//chatresponse.java

package com.example.llamachat;

import java.util.List;

public class ChatResponse {
    public List<Choice> choices;

    public String getAnswer() {
        return choices != null && !choices.isEmpty()
                ? choices.get(0).message.content
                : "⚠ No reply.";
    }

    public static class Choice {
        public Message message;
    }

    public static class Message {
        public String role;
        public String content;
    }
}


//dialogueactivity.java

package com.example.llamachat;

import android.os.Bundle;
import android.widget.EditText;
import android.widget.ImageView;

import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import java.util.ArrayList;

public class DialogueActivity extends AppCompatActivity {

    private RecyclerView conversationView;
    private EditText inputBox;
    private ImageView sendIcon;
    private ChatAdapter chatAdapter;
    private ArrayList<ChatItem> chatHistory;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_dialogue);

        conversationView = findViewById(R.id.conversationView);
        inputBox = findViewById(R.id.inputBox);
        sendIcon = findViewById(R.id.sendIcon);

        chatHistory = new ArrayList<>();
        chatAdapter = new ChatAdapter(chatHistory, getIntent().getStringExtra("userName"));
        conversationView.setLayoutManager(new LinearLayoutManager(this));
        conversationView.setAdapter(chatAdapter);

        sendIcon.setOnClickListener(v -> {
            String message = inputBox.getText().toString().trim();
            if (!message.isEmpty()) {
                chatHistory.add(new ChatItem(message, "user"));
                chatAdapter.notifyItemInserted(chatHistory.size() - 1);
                inputBox.setText("");

                BotBridge.askBot(message, reply -> runOnUiThread(() -> {
                    chatHistory.add(new ChatItem(reply, "bot"));
                    chatAdapter.notifyItemInserted(chatHistory.size() - 1);
                    conversationView.scrollToPosition(chatHistory.size() - 1);
                }));
            }
        });
    }
}

//mainactivity.java

package com.example.llamachat;

import android.content.Intent;
import android.os.Bundle;
import android.widget.Button;
import android.widget.EditText;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

public class MainActivity extends AppCompatActivity {
    EditText nameField;
    Button enterBtn;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        nameField = findViewById(R.id.nameField);
        enterBtn = findViewById(R.id.enterBtn);

        enterBtn.setOnClickListener(view -> {
            String enteredName = nameField.getText().toString().trim();
            if (!enteredName.isEmpty()) {
                Intent moveToChat = new Intent(MainActivity.this, DialogueActivity.class);
                moveToChat.putExtra("userName", enteredName);
                startActivity(moveToChat);
            } else {
                Toast.makeText(this, "Username required", Toast.LENGTH_SHORT).show();
            }
        });
    }
}


//talkservice.java


package com.example.llamachat;

import retrofit2.Call;
import retrofit2.http.Body;
import retrofit2.http.POST;

public interface TalkService {
    @POST("chat/completions")
    Call<ChatResponse> getReply(@Body ChatPrompt prompt);
}
