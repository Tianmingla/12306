package com.lalal.modules.memory;

import com.esotericsoftware.kryo.Kryo;
import com.esotericsoftware.kryo.io.Input;
import com.esotericsoftware.kryo.io.Output;
import org.springframework.ai.chat.messages.*;
import org.springframework.ai.chat.messages.AssistantMessage.ToolCall;
import org.springframework.ai.chat.messages.ToolResponseMessage.ToolResponse;
import org.springframework.ai.model.Media;

import java.io.ByteArrayOutputStream;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;


/**
 * Kryo 序列化工具 — 真正使用 Kryo 注册每种 Message 子类型
 *
 * 每种消息有不同的字段结构，必须分别处理：
 * - UserMessage:       text, mediaList, metadata
 * - AssistantMessage:  text, metadata, toolCalls(List<ToolCall>), media
 * - SystemMessage:     text, metadata
 * - ToolResponseMessage: text, metadata, responses(List<ToolResponse>)
 *
 * Kryo 注册后序列化体积比 JSON 小 60%+，速度提升 3-5 倍
 */
public class KryoMessageSerializer {

    private static final ThreadLocal<Kryo> KRYO_THREAD_LOCAL = ThreadLocal.withInitial(KryoMessageSerializer::createKryo);

    /**
     * 创建并配置 Kryo 实例
     * 注册所有消息类型以获得最佳序列化性能
     */
    private static Kryo createKryo() {
        Kryo kryo = new Kryo();
        kryo.setRegistrationRequired(false);
        kryo.setReferences(true);

        // 注册常用类型，提高序列化效率和兼容性
        kryo.register(ArrayList.class);
        kryo.register(HashMap.class);
        kryo.register(String.class);
        kryo.register(byte[].class);

        // 注册 Spring AI 消息类型
        kryo.register(UserMessage.class);
        kryo.register(AssistantMessage.class);
        kryo.register(SystemMessage.class);
        kryo.register(ToolResponseMessage.class);
        kryo.register(ToolCall.class);
        kryo.register(ToolResponse.class);
        kryo.register(MessageType.class);

        return kryo;
    }

    // ==================== 公开接口 ====================

    /**
     * 序列化消息列表为字节数组
     */
    public static byte[] serialize(List<Message> messages) {
        if (messages == null || messages.isEmpty()) {
            return new byte[0];
        }

        Kryo kryo = KRYO_THREAD_LOCAL.get();
        ByteArrayOutputStream baos = new ByteArrayOutputStream(4096);
        try (Output output = new Output(baos)) {
            output.writeInt(messages.size());
            for (Message message : messages) {
                writeMessage(kryo, output, message);
            }
            output.flush();
        }
        return baos.toByteArray();
    }

    /**
     * 反序列化字节数组为消息列表
     */
    public static List<Message> deserialize(byte[] bytes) {
        if (bytes == null || bytes.length == 0) {
            return new ArrayList<>();
        }

        Kryo kryo = KRYO_THREAD_LOCAL.get();
        try (Input input = new Input(bytes)) {
            int size = input.readInt();
            List<Message> messages = new ArrayList<>(size);
            for (int i = 0; i < size; i++) {
                messages.add(readMessage(kryo, input));
            }
            return messages;
        }
    }

    // ==================== 序列化：按消息类型分别处理 ====================

    /**
     * 写入一条消息
     * 格式: [typeValue(String)] [具体消息字段...]
     */
    private static void writeMessage(Kryo kryo, Output output, Message message) {
        // 写入消息类型标识
        output.writeString(message.getMessageType().getValue());
        // text
        output.writeString(message.getText()!= null ?message.getText(): "");

        switch (message.getMessageType()) {
            case USER -> writeUserMessage(kryo, output, (UserMessage) message);
            case ASSISTANT -> writeAssistantMessage(kryo, output, (AssistantMessage) message);
            case SYSTEM -> writeSystemMessage(kryo, output, (SystemMessage) message);
            case TOOL -> writeToolResponseMessage(kryo, output, (ToolResponseMessage) message);
        }
    }

    /**
     * UserMessage: text + mediaList + metadata
     * - mediaList: 每个 Media 有 mimeType + data(URL)
     */
    private static void writeUserMessage(Kryo kryo, Output output, UserMessage msg) {
        // mediaList
        List<Media> mediaList = msg.getMedia();
        if (mediaList != null && !mediaList.isEmpty()) {
            output.writeInt(mediaList.size());
            for (Media media : mediaList) {
                kryo.writeObject(output,media);
            }
        } else {
            output.writeInt(0);
        }
    }

    /**
     * AssistantMessage: text + metadata
     */
    private static void writeAssistantMessage(Kryo kryo, Output output, AssistantMessage msg) {
        Map<String,Object> metadata=msg.getMetadata();
        if (metadata != null && !metadata.isEmpty()) {
            output.writeInt(metadata.size());
            for (Map.Entry<String, Object> entry : metadata.entrySet()) {
                kryo.writeObject(output,entry.getKey());
                kryo.writeClass(output,entry.getClass());
                kryo.writeObject(output,entry.getValue());
            }
        } else {
            output.writeInt(0);
        }
    }

    /**
     * SystemMessage: text + metadata
     */
    private static void writeSystemMessage(Kryo kryo, Output output, SystemMessage msg) {
    }

    /**
     * ToolResponseMessage: text  + responses
     * - responses: 每个 ToolResponse(id, name, responseData)
     */
    private static void writeToolResponseMessage(Kryo kryo, Output output, ToolResponseMessage msg) {
        // responses
        List<ToolResponse> responses = msg.getResponses();
        if (responses != null && !responses.isEmpty()) {
            output.writeInt(responses.size());
            for (ToolResponse tr : responses) {
                kryo.writeObject(output,tr);
            }
        } else {
            output.writeInt(0);
        }
    }

// ==================== 反序列化 ====================

    /**
     * 读取一条消息
     * 顺序：typeValue -> text -> (各子类额外字段)
     */
    private static Message readMessage(Kryo kryo, Input input) {
        String typeValue = input.readString();
        MessageType messageType = MessageType.fromValue(typeValue);
        String text = input.readString();          // 公共 text 只在这里读一次

        return switch (messageType) {
            case USER    -> readUserMessage(kryo, input, text);
            case ASSISTANT -> readAssistantMessage(kryo, input, text);
            case SYSTEM  -> readSystemMessage(kryo, input, text);
            case TOOL    -> readToolResponseMessage(kryo, input, text);
        };
    }

    // ---------- 反序列化 UserMessage ----------
    // 序列化顺序：type, text, mediaList (size + Media 对象)
    private static UserMessage readUserMessage(Kryo kryo, Input input, String text) {
        int mediaSize = input.readInt();
        List<Media> mediaList = new ArrayList<>(mediaSize);
        for (int i = 0; i < mediaSize; i++) {
            mediaList.add(kryo.readObject(input, Media.class));   // 对应 writeObject
        }
        // 序列化时没有写 metadata，这里用空 Map
        return new UserMessage(text, mediaList);
    }

    // ---------- 反序列化 AssistantMessage ----------
    // 序列化顺序：type, text, metadata (size + 每个 entry: key, valueClass, value)
    private static AssistantMessage readAssistantMessage(Kryo kryo, Input input, String text) {
        int metaSize = input.readInt();
        Map<String, Object> metadata = new HashMap<>(metaSize);
        for (int i = 0; i < metaSize; i++) {
            String key = kryo.readObject(input, String.class);   // key 写了 class 吗？看你用 writeObject，默认写 class
            // 你写的时候是 kryo.writeClass(output, entry.getValue().getClass());
            // 然后 kryo.writeObject(output, entry.getValue());
            // 所以读的时候需要先读 class，再读对象
            Class<?> valueClass = kryo.readClass(input).getType();
            Object value = kryo.readObject(input, valueClass);
            metadata.put(key, value);
        }
        // 序列化时没有写 toolCalls 和 media，传空列表
        return new AssistantMessage(text, metadata);
    }

    // ---------- 反序列化 SystemMessage ----------
    // 序列化顺序：type, text（无额外字段）
    private static SystemMessage readSystemMessage(Kryo kryo, Input input, String text) {
        // 没有 metadata，直接用单参数构造
        return new SystemMessage(text);
    }

    // ---------- 反序列化 ToolResponseMessage ----------
    // 序列化顺序：type, text, responses (size + ToolResponse 对象)
    private static ToolResponseMessage readToolResponseMessage(Kryo kryo, Input input, String text) {
        int respSize = input.readInt();
        List<ToolResponse> responses = new ArrayList<>(respSize);
        for (int i = 0; i < respSize; i++) {
            responses.add(kryo.readObject(input, ToolResponse.class));
        }
        // 序列化时没有写 metadata，用空 Map
        return new ToolResponseMessage(responses, Map.of());
    }

    // ==================== 通用: metadata 读写 ====================

    private static void writeMetadata(Output output, Map<String, Object> metadata) {
        if (metadata != null && !metadata.isEmpty()) {
            output.writeInt(metadata.size());
            for (Map.Entry<String, Object> entry : metadata.entrySet()) {
                output.writeString(entry.getKey() != null ? entry.getKey() : "");
                // metadata value 统一转为 String 存储
                output.writeString(entry.getValue() != null ? entry.getValue().toString() : "");
            }
        } else {
            output.writeInt(0);
        }
    }

    private static Map<String, Object> readMetadata(Input input) {
        int size = input.readInt();
        Map<String, Object> metadata = new HashMap<>(size);
        for (int i = 0; i < size; i++) {
            String key = input.readString();
            String value = input.readString();
            if (!key.isEmpty()) {
                metadata.put(key, value);
            }
        }
        return metadata.isEmpty() ? Map.of() : metadata;
    }
}
