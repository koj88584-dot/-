const ASSISTANT_STORAGE_KEY = "hmdp_assistant_history";

new Vue({
  el: "#app",
  data: {
    scene: util.getUrlParam("scene") || "智能助手",
    draft: util.getUrlParam("prompt") || "",
    loading: false,
    assistantConfigured: null,
    autoPromptSent: false,
    autoExecutingKey: "",
    autoJumpTimer: null,
    openingState: {
      visible: false,
      label: "",
      description: ""
    },
    quickPrompts: [
      "帮我找附近评分高的火锅",
      "有没有可领取的优惠券",
      "帮我看一下待支付订单",
      "我想发一篇探店笔记",
      "帮我找一家附近的咖啡店",
      "打开我的优惠券"
    ],
    messages: []
  },
  computed: {
    sceneLabel() {
      return this.scene || "智能助手";
    },
    statusText() {
      if (this.assistantConfigured === true) return "DeepSeek 已连接";
      if (this.assistantConfigured === false) return "本地兜底模式";
      return "连接中";
    },
    statusClass() {
      if (this.assistantConfigured === true) return "connected";
      if (this.assistantConfigured === false) return "fallback";
      return "idle";
    }
  },
  mounted() {
    this.restoreConversation();
    this.$nextTick(() => {
      this.scrollToBottom();
      if (this.draft.trim() && !this.autoPromptSent) {
        this.autoPromptSent = true;
        this.submitMessage();
      }
    });
  },
  beforeDestroy() {
    this.cancelAutoExecution();
  },
  methods: {
    goBack() {
      history.back();
    },
    buildWelcomeMessage() {
      return {
        role: "assistant",
        content: "你好，我可以帮你找店、看订单、领优惠券、发笔记，也可以根据你的话直接带你跳到对应页面。",
        actions: [
          {
            type: "open_vouchers",
            label: "查看优惠券",
            description: "打开可领取优惠券页",
            path: "/pages/misc/vouchers.html?source=assistant&tab=available"
          },
          {
            type: "open_orders",
            label: "查看订单",
            description: "打开订单页",
            path: "/pages/order/orders.html?source=assistant&status=0"
          },
          {
            type: "create_blog",
            label: "发布笔记",
            description: "打开发笔记页面",
            path: "/pages/blog/blog-edit.html?source=assistant"
          }
        ],
        createdAt: Date.now(),
        executingHint: ""
      };
    },
    restoreConversation() {
      const parsed = util.readStorageJSON(ASSISTANT_STORAGE_KEY);
      if (Array.isArray(parsed) && parsed.length) {
        this.messages = parsed.map(item => Object.assign({ executingHint: "" }, item));
        const latestAssistant = parsed.slice().reverse().find(item => item.role === "assistant");
        if (latestAssistant && typeof latestAssistant.configured === "boolean") {
          this.assistantConfigured = latestAssistant.configured;
        }
        return;
      }
      this.messages = [this.buildWelcomeMessage()];
    },
    persistConversation() {
      util.writeStorageJSON(ASSISTANT_STORAGE_KEY, this.messages.slice(-24));
    },
    clearConversation() {
      this.cancelAutoExecution();
      this.$confirm("确定清空当前对话吗？", "提示", {
        confirmButtonText: "清空",
        cancelButtonText: "取消",
        type: "warning"
      }).then(() => {
        this.assistantConfigured = null;
        this.messages = [this.buildWelcomeMessage()];
        this.persistConversation();
        this.$nextTick(this.scrollToBottom);
      }).catch(() => {});
    },
    usePrompt(prompt) {
      this.draft = prompt;
      this.$nextTick(() => this.submitMessage());
    },
    submitMessage() {
      const content = this.draft.trim();
      if (!content || this.loading) {
        return;
      }

      this.cancelAutoExecution();
      const history = this.messages
        .filter(item => item.role === "assistant" || item.role === "user")
        .map(item => ({ role: item.role, content: item.content }))
        .slice(-10);

      this.messages.push({
        role: "user",
        content: content,
        actions: [],
        createdAt: Date.now()
      });
      this.draft = "";
      this.loading = true;
      this.persistConversation();
      this.$nextTick(this.scrollToBottom);

      const assistantMessage = {
        role: "assistant",
        content: "",
        actions: [],
        executingHint: "",
        configured: this.assistantConfigured,
        createdAt: Date.now()
      };
      this.messages.push(assistantMessage);
      this.persistConversation();
      this.streamAssistantReply(content, history, assistantMessage);
    },
    async streamAssistantReply(content, history, assistantMessage) {
      try {
        const response = await fetch(util.buildUrl(util.commonURL + "/assistant/chat/stream"), {
          method: "POST",
          headers: { "Content-Type": "application/json" },
          body: JSON.stringify({
            message: content,
            scene: this.sceneLabel,
            history: history
          })
        });

        if (!response.ok || !response.body) {
          throw new Error("Stream not available");
        }

        const reader = response.body.getReader();
        const decoder = new TextDecoder("utf-8");
        let buffer = "";
        let eventName = "";

        while (true) {
          const { value, done } = await reader.read();
          if (done) break;
          buffer += decoder.decode(value, { stream: true });
          const lines = buffer.split("\n");
          buffer = lines.pop() || "";
          for (const line of lines) {
            const trimmed = line.trim();
            if (!trimmed) {
              eventName = "";
              continue;
            }
            if (trimmed.startsWith("event:")) {
              eventName = trimmed.slice(6).trim();
              continue;
            }
            if (trimmed.startsWith("data:")) {
              const data = trimmed.slice(5).trim();
              this.handleStreamEvent(eventName || "delta", data, assistantMessage);
            }
          }
        }

        this.loading = false;
        this.persistConversation();
        this.$nextTick(this.scrollToBottom);
      } catch (error) {
        await this.requestAssistantReplyFallback(content, history, assistantMessage);
      } finally {
        this.loading = false;
      }
    },
    handleStreamEvent(eventName, data, assistantMessage) {
      if (!assistantMessage) {
        return;
      }
      if (eventName === "meta") {
        try {
          const meta = JSON.parse(data);
          this.assistantConfigured = meta.configured;
          assistantMessage.actions = Array.isArray(meta.actions) ? meta.actions : [];
          assistantMessage.configured = meta.configured;
        } catch (error) {
          console.warn("assistant meta parse failed", error);
        }
        return;
      }
      if (eventName === "done") {
        this.loading = false;
        this.persistConversation();
        this.scheduleAutoAction(assistantMessage);
        return;
      }
      if (this.loading) {
        this.loading = false;
      }
      assistantMessage.content += data || "";
      this.$nextTick(this.scrollToBottom);
    },
    async requestAssistantReplyFallback(content, history, assistantMessage) {
      try {
        const result = await axios.post("/assistant/chat", {
          message: content,
          scene: this.sceneLabel,
          history: history
        });
        const payload = util.extractResultData(result);
        this.assistantConfigured = payload.configured;
        assistantMessage.content = payload.reply || "这次没有拿到完整回复，请稍后再试。";
        assistantMessage.actions = Array.isArray(payload.actions) ? payload.actions : [];
        assistantMessage.executingHint = "";
        assistantMessage.configured = payload.configured;
        this.persistConversation();
        this.scheduleAutoAction(assistantMessage);
      } catch (error) {
        const fallbackMessage = typeof error === "string" ? error : "智能助手暂时不可用，请稍后再试。";
        this.assistantConfigured = false;
        assistantMessage.content = fallbackMessage;
        assistantMessage.actions = [
          { label: "去搜索页", description: "搜索店铺、地点和笔记", path: "/pages/misc/search.html" },
          { label: "查看优惠券", description: "看看当前能领哪些券", path: "/pages/misc/vouchers.html" }
        ];
        assistantMessage.executingHint = "";
        assistantMessage.configured = false;
        this.persistConversation();
        this.$message.error("智能助手暂时不可用，请稍后重试");
      }
    },
    handleAction(action) {
      if (!action || !action.path) {
        return;
      }
      this.cancelAutoExecution();
      this.queueActionOpen(action);
    },
    cancelAutoExecution() {
      if (this.autoJumpTimer) {
        clearTimeout(this.autoJumpTimer);
        this.autoJumpTimer = null;
      }
      this.autoExecutingKey = "";
      this.openingState.visible = false;
    },
    pickPrimaryAction(actions) {
      if (!Array.isArray(actions) || !actions.length) {
        return null;
      }
      return actions.find(item => item && item.autoRun) || actions[0];
    },
    scheduleAutoAction(message) {
      if (!message || !Array.isArray(message.actions) || !message.actions.length) {
        return;
      }
      const primary = this.pickPrimaryAction(message.actions);
      if (!primary || !primary.autoRun) {
        return;
      }
      const actionKey = [message.createdAt, primary.type || "", primary.path || ""].join(":");
      if (this.autoExecutingKey === actionKey) {
        return;
      }
      this.cancelAutoExecution();
      this.autoExecutingKey = actionKey;
      this.$set(message, "executingHint", this.getExecutingHint(primary));
      this.persistConversation();
      this.$nextTick(this.scrollToBottom);
      this.queueActionOpen(primary);
    },
    queueActionOpen(action) {
      const label = action && action.label ? action.label : "目标页面";
      this.openingState = {
        visible: true,
        label: label,
        description: this.getOpeningDescription(action)
      };
      this.autoJumpTimer = setTimeout(() => {
        location.href = this.buildActionUrl(action);
      }, 1000);
    },
    getExecutingHint(action) {
      const label = action && action.label ? action.label : "目标页面";
      return "正在为你打开“" + label + "”，稍后会自动跳转。";
    },
    getOpeningDescription(action) {
      if (!action) {
        return "马上跳转到对应页面。";
      }
      return action.description || "马上帮你切到对应页面。";
    },
    buildActionUrl(action) {
      if (!action) {
        return "";
      }
      if (action.path && action.path.indexOf("?") > -1) {
        return action.path;
      }
      return util.buildUrl(action.path || "", action.params || {});
    },
    formatMessage(content) {
      return util.escapeHtml(content || "").replace(/\n/g, "<br>");
    },
    formatTime(time) {
      if (!time) {
        return "";
      }
      const date = new Date(time);
      const hour = String(date.getHours()).padStart(2, "0");
      const minute = String(date.getMinutes()).padStart(2, "0");
      return hour + ":" + minute;
    },
    scrollToBottom() {
      window.scrollTo({
        top: document.documentElement.scrollHeight,
        behavior: "smooth"
      });
    }
  }
});
