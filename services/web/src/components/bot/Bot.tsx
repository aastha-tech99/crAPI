/*
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *         http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

import React, { useState, useEffect } from "react";
import ChatBot, { Params } from "react-chatbotify";
import MarkdownRenderer, {
  MarkdownRendererBlock,
} from "@rcb-plugins/markdown-renderer";
import { APIService } from "../../constants/APIConstant";
import { Row, Col } from "antd";
import {
  CloseSquareOutlined,
  ExpandAltOutlined,
  WechatWorkOutlined,
  DeleteOutlined,
} from "@ant-design/icons";
import "./chatbot.css";
import chatbotIcon from "../../assets/chatbot.svg";

const ChatBotIcon = () => {
  return (
    <img
      src={chatbotIcon}
      alt="Chatbot"
      style={{ width: "50px", height: "50px", color: "#ffffff" }}
    />
  );
};

const superagent = require("superagent");

interface ChatMessage {
  id: number;
  role: string;
  content: string;
}

interface ChatBotState {
  openapiKey: string | null;
  initializing: boolean;
  initializationRequired: boolean;
  accessToken: string;
  isLoggedIn: boolean;
  role: string;
  messages: ChatMessage[];
}

interface ChatBotComponentProps {
  accessToken: string;
  isLoggedIn: boolean;
  role: string;
}

const ChatBotComponent: React.FC<ChatBotComponentProps> = (props) => {
  const [expanded, setExpanded] = useState<boolean>(false);
  const [chatResetKey, setChatResetKey] = useState<number>(0);
  const helpOptions = ["Initialize", "Clear", "Help"];
  const [apiKey, setApiKey] = useState("");

  const [chatbotState, setChatbotState] = useState<ChatBotState>({
    openapiKey: localStorage.getItem("openapi_key"),
    initializing: false,
    initializationRequired: false,
    accessToken: props.accessToken,
    isLoggedIn: props.isLoggedIn,
    role: props.role,
    messages: [],
  });

  const handleApiKey = (event: React.ChangeEvent<HTMLInputElement>) => {
    setApiKey(event.target.value); // Update state
  };

  // Handle initialization
  const handleInitialization = async (apiKey: string) => {
    try {
      const initUrl = APIService.CHATBOT_SERVICE + "genai/init";
      const response = await superagent
        .post(initUrl)
        .set("Accept", "application/json")
        .set("Content-Type", "application/json")
        .set("Authorization", `Bearer ${props.accessToken}`)
        .send({ openai_api_key: apiKey });

      console.log("Initialization response:", response.body);
      return response.body.success || response.status === 200;
    } catch (err) {
      console.error("Error initializing chatbot:", err);
      return false;
    }
  };

  // Fetch chat history from backend
  const fetchChatHistory = async () => {
    try {
      const stateUrl = APIService.CHATBOT_SERVICE + "genai/state";
      const response = await superagent
        .get(stateUrl)
        .set("Accept", "application/json")
        .set("Content-Type", "application/json")
        .set("Authorization", `Bearer ${props.accessToken}`);

      console.log("Chat history response:", response.body);
      return response.body.chat_history || [];
    } catch (err) {
      console.error("Error fetching chat history:", err);
      return [];
    }
  };

  // Clear chat history
  const clearChatHistory = async () => {
    try {
      const clearUrl = APIService.CHATBOT_SERVICE + "genai/reset";
      await superagent
        .post(clearUrl)
        .set("Accept", "application/json")
        .set("Content-Type", "application/json")
        .set("Authorization", `Bearer ${props.accessToken}`)
        .send();

      console.log("Chat history cleared");
      return true;
    } catch (err) {
      console.error("Error clearing chat history:", err);
      return false;
    }
  };

  // Handle user messages
  const handleUserMessage = async (message: string) => {
    try {
      const chatUrl = APIService.CHATBOT_SERVICE + "genai/ask";
      console.log("Sending message to:", chatUrl);
      console.log("Message:", message);

      const response = await superagent
        .post(chatUrl)
        .set("Accept", "application/json")
        .set("Content-Type", "application/json")
        .set("Authorization", `Bearer ${props.accessToken}`)
        .send({ message });

      console.log("API Response:", response.body);

      // Check different possible response formats
      let botResponse = "";
      if (response.body.response) {
        botResponse = response.body.response;
      } else if (response.body.answer) {
        botResponse = response.body.answer;
      } else if (response.body.reply) {
        botResponse = response.body.reply;
      } else if (response.body.message) {
        botResponse = response.body.message;
      } else if (typeof response.body === "string") {
        botResponse = response.body;
      } else {
        console.log("Unexpected response format:", response.body);
        botResponse =
          "I received your message but couldn't process the response format. Please try again.";
      }

      console.log("Bot response to render:", botResponse);
      console.log(
        "Testing markdown in response:",
        botResponse.includes("**") ||
          botResponse.includes("*") ||
          botResponse.includes("#"),
      );
      return botResponse;
    } catch (err) {
      console.error("Error in chat API:", err);
      console.error(
        "Error details:",
        (err as any).response?.body || (err as any).message,
      );
      return "Sorry, I encountered an error. Please try again.";
    }
  };

  // React Chatbotify flow configuration
  const flow = {
    start: {
      message: "Welcome to crAPI! How can I assist you today?",
      transition: { duration: 1000 },
      path: "check_initialization",
    },
    check_initialization: {
      transition: { duration: 0 },
      chatDisabled: true,
      path: (params: Params) => {
        return (async () => {
          // Check if chatbot is already initialized
          try {
            const stateUrl = APIService.CHATBOT_SERVICE + "genai/state";
            const response = await superagent
              .get(stateUrl)
              .set("Accept", "application/json")
              .set("Content-Type", "application/json")
              .set("Authorization", `Bearer ${props.accessToken}`);

            const isInitialized =
              response.body.initialized === "true" ||
              response.body.initialized === true;

            if (isInitialized) {
              // Fetch and display chat history
              const chatHistory = await fetchChatHistory();
              console.log("Chat history:", chatHistory);
              setChatbotState((prev) => ({
                ...prev,
                messages: chatHistory,
                initializationRequired: false,
              }));

              if (chatHistory.length > 0) {
                // inject all the messages in the chat history
                for (const message of chatHistory) {
                  await params.injectMessage(
                    message.content,
                    message.role === "user" ? "user" : "bot",
                  );
                }
                await params.injectMessage(
                  `Loaded ${chatHistory.length} previous messages!`,
                );
              }

              return "chat";
            } else {
              await params.injectMessage(
                "Chatbot is not initialized. Please choose an option:",
              );
              return "show_options";
            }
          } catch (err) {
            console.error("Error checking initialization:", err);
            await params
              .injectMessage(
                "Unable to check initialization status. Please choose an option:",
              )
              .catch((e: unknown) =>
                console.error("Error injecting error message:", e),
              );
            return "show_options";
          }
        })().catch((e) => {
          console.error("Unhandled error in check_initialization handler:", e);
          return "show_options";
        });
      },
      renderMarkdown: ["BOT"],
    },
    show_options: {
      message: "What would you like to do?",
      options: helpOptions,
      path: "process_options",
    },
    process_options: {
      transition: { duration: 0 },
      chatDisabled: true,
      path: (params: Params) => {
        return (async () => {
          try {
            switch (params.userInput) {
              case "Initialize":
                await params.injectMessage(
                  "Please type your OpenAI API key below and enter 'Submit' in the chat to initialize the chatbot.",
                );
                return "initialize";
              case "Clear":
                await params.injectMessage("Clearing the chat history...");
                const cleared = await clearChatHistory();
                if (cleared) {
                  await params.injectMessage(
                    "Chat history cleared successfully!",
                  );
                  setChatbotState((prev) => ({ ...prev, messages: [] }));
                } else {
                  await params.injectMessage(
                    "Failed to clear chat history. Please try again.",
                  );
                }
                return "show_options";
              case "Help":
                await params.injectMessage(`**crAPI Chatbot Help**

**Available Commands:**
- **Initialize**: Set up the chatbot with your OpenAI API key
- **Clear**: Clear the chat history
- **Help**: Show this help message

**Usage:**
1. First, initialize the chatbot with your OpenAI API key
2. Once initialized, you can ask questions about crAPI
3. Use the Clear option to reset your chat history

What would you like to do next?`);
                return "show_options";
              default:
                await params.injectMessage(
                  "Invalid option. Please choose from the available options.",
                );
                return "show_options";
            }
          } catch (err) {
            console.error("Error processing option:", err);
            await params
              .injectMessage(
                "An error occurred while processing your selection. Please try again.",
              )
              .catch((e: unknown) =>
                console.error("Error injecting error message:", e),
              );
            return "show_options";
          }
        })().catch((e) => {
          console.error("Unhandled error in process_options handler:", e);
          return "show_options";
        });
      },
      renderMarkdown: ["BOT"],
    },
    initialize: {
      component: (
        <input
          type="password"
          placeholder="Please paste your OpenAI API key here"
          onChange={handleApiKey}
        />
      ),
      path: (params: Params) => {
        return (async () => {
          try {
            const APIKey = apiKey.trim();
            if (!APIKey) {
              await params.injectMessage(
                "API key cannot be empty. Please enter a valid OpenAI API key and enter 'Submit' in the chat.",
              );
              return "initialize";
            }
            if (params.userInput.toLowerCase() !== "submit") {
              await params.injectMessage(
                "Please type 'Submit' to confirm your API key.",
              );
              return;
            }
            const success = await handleInitialization(APIKey);
            if (success) {
              // Fetch chat history after successful initialization
              const chatHistory = await fetchChatHistory();
              setChatbotState((prev) => ({
                ...prev,
                messages: chatHistory,
                initializationRequired: false,
              }));

              if (chatHistory.length > 0) {
                await params.simulateStreamMessage(
                  `✅ Chatbot initialized successfully! Loaded ${chatHistory.length} previous messages. You can now start chatting!`,
                );
              } else {
                await params.injectMessage(
                  "✅ Chatbot initialized successfully! Ready to chat! Ask me anything about crAPI.",
                );
              }
              return "chat";
            } else {
              await params.injectMessage(
                "❌ Failed to initialize chatbot. Please check your API key and try again:",
              );
              return "show_options";
            }
          } catch (err) {
            console.error("Error during initialization flow:", err);
            await params
              .injectMessage(
                "An error occurred during initialization. Please try again.",
              )
              .catch((e: unknown) =>
                console.error("Error injecting error message:", e),
              );
            return "show_options";
          }
        })().catch((e) => {
          console.error("Unhandled error in initialize handler:", e);
          return "show_options";
        });
      },
      renderMarkdown: ["BOT"],
    },
    chat: {
      function: (params: Params) => {
        return (async () => {
          try {
            const response = await handleUserMessage(params.userInput);
            await params.injectMessage(response);
          } catch (err) {
            console.error("Error processing chat message:", err);
            await params
              .injectMessage(
                "Sorry, something went wrong. Please try again.",
              )
              .catch((e: unknown) =>
                console.error("Error injecting error message:", e),
              );
          }
        })().catch((e) => console.error("Unhandled error in chat handler:", e));
      },
      renderMarkdown: ["BOT"],
      path: "chat",
    },
  };

  const plugins = [MarkdownRenderer()];

  // React Chatbotify settings
  const settings = {
    general: {
      primaryColor: "#8b5cf6",
      secondaryColor: "#a855f7",
      fontFamily:
        "-apple-system, BlinkMacSystemFont, 'Segoe UI', Roboto, sans-serif",
      embedded: false,
    },
    chatHistory: {
      storageKey: `chat_history`,
    },
    chatWindow: {
      showScrollbar: true,
      showHeader: true,
      showFooter: true,
      showChatButton: true,
      showChatInput: true,
      showChatHistory: true,
      showChatWindow: true,
    },
    chatInput: {
      placeholder: "Type your message here...",
      enabledPlaceholderText: "Type your message here...",
      showCharacterCount: false,
      allowNewlines: true,
      sendButtonStyle: {
        background: "#10b981",
      },
    },
    botBubble: {
      showAvatar: true,
      allowMarkdown: true,
      animate: true,
      avatar: "🤖",
    },
    header: {
      title: (
        <div
          style={{
            display: "flex",
            alignItems: "center",
            justifyContent: "space-between",
            width: "100%",
          }}
        >
          <span
            style={{ fontSize: "18px", fontWeight: "600", color: "#1f2937" }}
          >
            crAPI ChatBot
          </span>
          <div style={{ display: "flex", alignItems: "center", gap: "8px" }}>
            <button
              className="delete-chat-btn"
              onClick={() => {
                (async () => {
                try {
                  // throw a beautiful popup to confirm the action
                  const { confirm } = await import("antd").then(
                    ({ Modal }) => ({
                      confirm: Modal.confirm,
                    }),
                  );

                  confirm({
                    title:
                      "Are you sure you want to clear the chat history from crAPI servers?",
                    content: "This action cannot be undone.",
                    onOk: () => {
                      (async () => {
                      try {
                        // Clear UI immediately by forcing re-render
                        setChatResetKey((prev) => prev + 1);

                        // Clear local storage for chat history
                        const storageKey = `react_chatbot_history_${chatbotState.initializationRequired ? "pending" : "active"}`;
                        localStorage.removeItem(storageKey);

                        // Also clear backend history
                        const success = await clearChatHistory();
                        if (!success) {
                          // If backend clear failed, show error but keep UI cleared
                          console.error(
                            "Failed to clear backend chat history",
                          );
                        }
                      } catch (err) {
                        console.error("Error clearing chat history:", err);
                      }
                      })().catch((e) => console.error("Unhandled error in onOk handler:", e));
                    },
                    onCancel: () => {},
                  });
                } catch (err) {
                  console.error(
                    "Error opening clear history confirmation:",
                    err,
                  );
                }
                })().catch((e) => console.error("Unhandled error in delete handler:", e));
              }}
              aria-label="Clear Chat History"
              title="Clear Chat History"
              onMouseEnter={(e) => {
                e.currentTarget.style.transform = "scale(1.05)";
              }}
              onMouseLeave={(e) => {
                e.currentTarget.style.transform = "scale(1)";
              }}
            >
              <DeleteOutlined />
            </button>
            <button
              className="expand-chatbot-btn"
              onClick={() => setExpanded((prev) => !prev)}
              aria-label={expanded ? "Collapse Chatbot" : "Expand Chatbot"}
              title={expanded ? "Collapse Chatbot" : "Expand Chatbot"}
              onMouseEnter={(e) => {
                e.currentTarget.style.transform = "scale(1.05)";
              }}
              onMouseLeave={(e) => {
                e.currentTarget.style.transform = "scale(1)";
              }}
            >
              <ExpandAltOutlined />
            </button>
          </div>
        </div>
      ),
    },
    notification: {
      disabled: true,
    },
    audio: {
      disabled: true,
    },
    chatButton: {
      icon: ChatBotIcon,
    },
    userBubble: {
      animate: true,
      showAvatar: true,
      avatar: "👤",
    },
  };

  // Reset chatbot state when session changes (token/role rotation)
  // to prevent stale session usage after privilege change (CWE-384)
  useEffect(() => {
    console.log("ChatBot component initialized");
    setChatbotState((prev) => ({
      ...prev,
      accessToken: props.accessToken,
      isLoggedIn: props.isLoggedIn,
      role: props.role,
      messages: [],
    }));
    setChatResetKey((prev) => prev + 1);
  }, [props.accessToken, props.isLoggedIn, props.role]);

  return (
    <Row>
      <Col xs={10}>
        <div className={`app-chatbot-container${expanded ? " expanded" : ""}`}>
          <ChatBot
            key={chatResetKey}
            flow={flow}
            plugins={plugins}
            settings={settings}
            styles={{
              chatWindowStyle: {
                width: expanded ? "max(50vw, 500px)" : "420px",
                height: expanded ? "90vh" : "70vh",
                borderRadius: "16px",
                boxShadow: expanded
                  ? "0 20px 60px rgba(0, 0, 0, 0.2)"
                  : "0 20px 40px rgba(0, 0, 0, 0.1)",
                border: "1px solid #e5e7eb",
                background: "#ffffff",
              },
              chatInputAreaStyle: {
                padding: "20px 24px",
                background: "#ffffff",
                borderTop: "1px solid #f3f4f6",
              },
              sendButtonStyle: {
                background: "#10b981",
                width: "44px",
                height: "44px",
                borderRadius: "50%",
                marginLeft: "12px",
                border: "none",
                color: "#ffffff",
                cursor: "pointer",
                display: "flex",
                alignItems: "center",
                justifyContent: "center",
              },
              botBubbleStyle: {
                background: "#f3f4f6",
                color: "#374151",
                borderRadius: "16px",
                padding: "12px 16px",
                margin: "8px 0",
                fontFamily:
                  "-apple-system, BlinkMacSystemFont, 'Segoe UI', Roboto, sans-serif",
                fontSize: "14px",
                lineHeight: "1.4",
              },
              userBubbleStyle: {
                background: "#8b5cf6",
                color: "#ffffff",
                borderRadius: "16px",
                padding: "12px 16px",
                margin: "8px 0",
                fontFamily:
                  "-apple-system, BlinkMacSystemFont, 'Segoe UI', Roboto, sans-serif",
                fontSize: "14px",
                lineHeight: "1.4",
              },
              fileAttachmentButtonDisabledStyle: {
                display: "none",
              },
            }}
          />
        </div>
      </Col>
    </Row>
  );
};

export default ChatBotComponent;
