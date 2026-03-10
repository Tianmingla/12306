
import { GoogleGenAI, Chat, GenerateContentResponse } from "@google/genai";

let chatSession: Chat | null = null;

const getAIClient = () => {
  const apiKey = process.env.API_KEY;
  if (!apiKey) {
    console.error("API_KEY is missing");
    return null;
  }
  return new GoogleGenAI({ apiKey });
};

export const initializeChat = async () => {
  const ai = getAIClient();
  if (!ai) return null;

  try {
    chatSession = ai.chats.create({
      model: 'gemini-2.5-flash',
      config: {
        systemInstruction: `你是一个乐于助人的铁路出行助手，名字叫“智行”。
        你的目标是帮助用户解决在中国铁路出行方面的问题。
        
        主要能力：
        1. 解释票务政策（退票、改签、学生票、儿童票等）。
        2. 推荐旅游行程或热门目的地。
        3. 提供城市间的大致旅行时间（例如：北京到上海的高铁大约需要4.5小时）。
        4. 始终使用中文回复，语气亲切友好，适当使用Emoji。
        
        如果被问及具体的实时余票或座位情况，请解释你目前是演示助手，暂时无法访问实时库存数据库。`,
        temperature: 0.7,
      },
    });
    return chatSession;
  } catch (error) {
    console.error("Failed to initialize chat", error);
    return null;
  }
};

export const sendMessageToGemini = async (message: string): Promise<string> => {
  if (!chatSession) {
    await initializeChat();
  }

  if (!chatSession) {
    return "抱歉，暂时无法连接智能客服服务，请稍后再试。";
  }

  try {
    const response: GenerateContentResponse = await chatSession.sendMessage({ message });
    return response.text || "我没有听清，请您再说一遍？";
  } catch (error) {
    console.error("Error sending message to Gemini:", error);
    return "抱歉，处理您的请求时遇到了错误。";
  }
};
