# How to test the assistant and saved chats

1. Sign in and open AI Assistant. Use **+ → Upload a document** to attach `assistant-test-data.md`.
2. Send: **Using only the attached fictional portfolio, summarize the monthly total, annual equivalent, largest category, and most expensive plan. Cite the document. Keep this sample separate from my real account.**
   Expected: INR 1,594 monthly; INR 19,128 annually; Productivity INR 698; WorkFlow Pro INR 499.
3. Ask: **What are the StreamNest cancellation rules in the attached document?**
   Expected: request at least seven days before renewal, access through the paid period, no partial-month refund.
4. Ask: **According to the attached document, how much could I save by downgrading WorkFlow Pro to Starter? Do not change anything.**
   Expected: INR 200 monthly / INR 2,400 annually, with no account change.
5. Refresh the page. The current conversation should reopen with its previous messages and references. Select **New chat**, then **Saved chats** to reopen the earlier conversation.
6. Continue with: **Remind me which fictional plan we discussed downgrading, and why.** The assistant should use the stored conversation context. If you selected New chat, this context will only return when you reopen the old chat.
7. For real account charts, ask: **Chart my actual monthly spending by category. Do not use amounts from the sample document.** Chart data must come from your own saved subscriptions. An empty account should show an empty chart, not the sample's amounts.

To test chart values against the fictional portfolio, manually add the six sample plans to a separate empty test account first. Then ask **Compare my subscription costs in a chart**. Expected monthly total: INR 1,594. Document upload itself does not import subscription records. TXT/Markdown uploads are supported; CSV import is not implemented.

Privacy check: sign out, sign in as a different test user, and open Saved chats. The first user's conversations and documents must not appear. Do not expect previous browser-only chats from before this update to be recovered; they were never saved to the database.
