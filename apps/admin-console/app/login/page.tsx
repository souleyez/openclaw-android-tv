import { Panel } from "@/components/panel";
import { requestAdminCodeAction, verifyAdminCodeAction } from "@/app/login/actions";

export const dynamic = "force-dynamic";

type LoginPageProps = {
  searchParams?: Promise<{
    email?: string;
    sent?: string;
  }>;
};

export default async function LoginPage({ searchParams }: LoginPageProps) {
  const params = (await searchParams) ?? {};
  const email = params.email ?? "soulzyn@outlook.com";
  const sent = params.sent === "1";

  return (
    <main className="login-page">
      <section className="login-hero">
        <p className="login-hero__eyebrow">OpenClaw 管理后台</p>
        <h1 className="login-hero__title">邮箱验证码登录</h1>
        <p className="login-hero__body">
          当前只开放白名单管理员邮箱。先发送验证码到邮箱，再输入验证码进入后台。
          登录成功后会默认记住当前设备，平时不用重复登录。
        </p>
      </section>

      <div className="login-grid">
        <Panel title="发送验证码" subtitle="默认白名单已包含 soulzyn@outlook.com。">
          <form action={requestAdminCodeAction} className="stack-form">
            <div className="toolbar-form__field">
              <label className="toolbar-form__label" htmlFor="login-email">
                邮箱
              </label>
              <input className="toolbar-form__input" defaultValue={email} id="login-email" name="email" placeholder="soulzyn@outlook.com" />
            </div>
            <div className="toolbar-form__actions">
              <button className="toolbar-form__button" type="submit">
                发送验证码
              </button>
            </div>
          </form>
        </Panel>

        <Panel title="输入验证码" subtitle={sent ? "验证码已发送，请查看邮箱。" : "收到验证码后，在这里完成登录。"}>
          <form action={verifyAdminCodeAction} className="stack-form">
            <input name="email" type="hidden" value={email} />
            <div className="toolbar-form__field">
              <label className="toolbar-form__label" htmlFor="verify-email">
                当前邮箱
              </label>
              <input className="toolbar-form__input" defaultValue={email} id="verify-email" readOnly />
            </div>
            <div className="toolbar-form__field">
              <label className="toolbar-form__label" htmlFor="verify-code">
                验证码
              </label>
              <input className="toolbar-form__input" id="verify-code" name="code" placeholder="6 位验证码" />
            </div>
            <div className="toolbar-form__actions">
              <button className="toolbar-form__button" type="submit">
                验证并登录
              </button>
            </div>
          </form>
        </Panel>
      </div>
    </main>
  );
}
