(function () {
  'use strict';

  const $ = window.jQuery;
  const percent = value => value === '' || (Number.isFinite(Number(value)) && Number(value) >= 0 && Number(value) <= 100);

  function announce(form, message) {
    let box = form.querySelector('.form-validation-message');
    if (!box) {
      box = document.createElement('p');
      box.className = 'form-validation-message notice error';
      box.setAttribute('role', 'alert');
      box.setAttribute('aria-live', 'assertive');
      box.tabIndex = -1;
      form.prepend(box);
    }
    box.hidden = false;
    box.tabIndex = -1;
    box.textContent = message;
    box.focus({ preventScroll: true });
    box.scrollIntoView({ behavior: 'smooth', block: 'center' });
  }

  function clearFieldError(input) {
    input.removeAttribute('aria-invalid');
    input.setCustomValidity('');
    const hintId = input.getAttribute('aria-describedby');
    if (hintId) {
      const hint = document.getElementById(hintId);
      if (hint && hint.dataset.fieldError) hint.textContent = '';
    }
  }

  function setFieldError(input, message) {
    input.setAttribute('aria-invalid', 'true');
    input.setCustomValidity(message);
    const hintId = input.getAttribute('aria-describedby');
    if (hintId) {
      const hint = document.getElementById(hintId);
      if (hint && hint.dataset.fieldError) hint.textContent = message;
    }
  }

  function validateRanges(form, submit) {
    const pairs = [['oilMin', 'oilMax'], ['componentMin', 'componentMax']];
    const message = form.querySelector('[data-range-message]');
    for (const [minName, maxName] of pairs) {
      const min = form.elements[minName] ? form.elements[minName].value : '';
      const max = form.elements[maxName] ? form.elements[maxName].value : '';
      if (!percent(min) || !percent(max)) {
        if (message) {
          message.textContent = '百分比须在 0–100 之间。';
          message.classList.add('invalid');
        }
        if (submit) {
          submit.preventDefault();
          const invalid = !percent(min) ? form.elements[minName] : form.elements[maxName];
          invalid.focus();
        }
        return false;
      }
      if (min !== '' && max !== '' && Number(min) > Number(max)) {
        if (message) {
          message.textContent = '范围下限不能大于上限。';
          message.classList.add('invalid');
        }
        if (submit) {
          submit.preventDefault();
          form.elements[minName].focus();
        }
        return false;
      }
    }
    if (message) {
      message.textContent = '';
      message.classList.remove('invalid');
    }
    return true;
  }

  function validateComponents(text, allowUnknown) {
    for (const line of text.split(/\r?\n/)) {
      if (!line.trim()) continue;
      const pair = line.split('=');
      if (pair.length !== 2 || !pair[0].trim()) return false;
      if (pair[1].trim() === '' && allowUnknown) continue;
      if (!percent(pair[1].trim())) return false;
    }
    return true;
  }

  function validateForm(form, event) {
    const type = form.dataset.validate;
    const f = form.elements;
    const fail = message => {
      event.preventDefault();
      announce(form, message);
    };

    if (type === 'login') {
      if (!f.username.value.trim()) {
        setFieldError(f.username, '请输入用户名。');
        fail('请填写用户名。');
      } else if (!f.password.value) {
        setFieldError(f.password, '请输入密码。');
        fail('请填写密码。');
      }
    }

    if (type === 'register') {
      const username = f.username.value.trim();
      if (!/^[A-Za-z][A-Za-z0-9_]{3,31}$/.test(username)) return fail('用户名需为 4–32 位，以字母开头，只能包含字母、数字和下划线。');
      if (f.password.value.length < 10 || f.password.value.length > 72 || !/[A-Za-z]/.test(f.password.value) || !/[0-9]/.test(f.password.value)) return fail('密码需为 10–72 位，并同时包含字母和数字。');
      if (f.password.value !== f.confirmPassword.value) return fail('两次输入的密码不一致。');
      if (!f.displayName.value.trim()) return fail('请填写显示名称。');
      if (f.email.value && !f.email.checkValidity()) return fail('邮箱格式不正确。');
    }

    if (type === 'password') {
      if (f.oldPassword && !f.oldPassword.value) return fail('请输入当前密码。');
      if (f.password.value.length < 10 || f.password.value.length > 72 || !/[A-Za-z]/.test(f.password.value) || !/[0-9]/.test(f.password.value)) return fail('新密码需为 10–72 位，并同时包含字母和数字。');
      if (f.password.value !== f.confirmPassword.value) return fail('两次输入的新密码不一致。');
    }

    if (type === 'profile') {
      if (!f.displayName.value.trim() || f.displayName.value.length > 80) return fail('显示名称需为 1–80 个字符。');
      if (f.email.value && !f.email.checkValidity()) return fail('邮箱格式不正确。');
    }

    if (type === 'ranges' && !validateRanges(form, event)) return;

    if (type === 'sample' || type === 'plant') {
      if (type === 'plant' && !f.nameZh.value.trim()) return fail('中文名称为必填项。');
      const oil = f.oilContent ? f.oilContent.value : '';
      if (!percent(oil)) return fail('含油率必须在 0–100 之间。');
      if (f.components && !validateComponents(f.components.value, true)) return fail('脂肪酸请按“名称=百分比”逐行填写，百分比须在 0–100 之间；未知值可留空。');
    }

    if (type === 'upload') {
      const fileInput = f.image;
      const file = fileInput && fileInput.files ? fileInput.files[0] : null;
      if (!file) return fail('请选择要上传的植物图片。');
      if (file.size > 5 * 1024 * 1024 || !validImage(file)) return fail('请选择不超过 5 MB 的 JPG、PNG 或 WebP 图片。');
    }
  }

  function validImage(file) {
    const allowed = ['image/jpeg', 'image/png', 'image/webp'];
    const extAllowed = /\.(jpe?g|png|webp)$/i.test(file.name || '');
    return allowed.includes(file.type) || (!file.type && extAllowed);
  }

  function initUsernameCheck() {
    const input = document.getElementById('username');
    const hint = document.getElementById('usernameHint');
    if (!input || !hint || !$) return;
    let request = null;
    let checkedValue = '';
    const check = () => {
      const value = input.value.trim();
      if (!/^[A-Za-z][A-Za-z0-9_]{3,31}$/.test(value)) {
        hint.textContent = value ? '用户名格式不正确。' : '4–32 位，以字母开头，使用字母、数字或下划线。';
        hint.classList.toggle('invalid', Boolean(value));
        hint.classList.remove('valid');
        input.removeAttribute('aria-busy');
        return;
      }
      if (value === checkedValue) return;
      checkedValue = value;
      if (request && request.readyState !== 4) request.abort();
      input.setAttribute('aria-busy', 'true');
      hint.textContent = '正在检查用户名…';
      hint.classList.remove('valid', 'invalid');
      request = $.ajax({
        url: (window.APP_CONTEXT || '') + '/register/check-username',
        data: { username: value },
        dataType: 'json',
        timeout: 8000
      }).done(data => {
        if (input.value.trim() !== value) return;
        hint.textContent = data.message || (data.available ? '用户名可用' : '用户名已使用');
        hint.classList.toggle('valid', Boolean(data.available));
        hint.classList.toggle('invalid', !data.available);
        input.setAttribute('aria-invalid', String(!data.available));
      }).fail((xhr, status) => {
        if (status === 'abort' || input.value.trim() !== value) return;
        hint.textContent = '暂时无法验证用户名；提交时服务器仍会检查。';
        hint.classList.remove('valid');
        hint.classList.add('invalid');
      }).always(() => {
        if (input.value.trim() === value) input.removeAttribute('aria-busy');
      });
    };
    input.addEventListener('blur', check);
    input.addEventListener('input', () => {
      if (request && request.readyState !== 4) request.abort();
      checkedValue = '';
      input.removeAttribute('aria-busy');
      input.removeAttribute('aria-invalid');
      hint.classList.remove('valid', 'invalid');
    });
  }

  function init() {
    const navToggle = document.querySelector('.nav-toggle');
    const nav = document.getElementById('main-nav');
    if (navToggle && nav) {
      navToggle.addEventListener('click', () => {
        const open = nav.classList.toggle('open');
        navToggle.setAttribute('aria-expanded', String(open));
        navToggle.setAttribute('aria-label', open ? '关闭导航' : '打开导航');
      });
      nav.addEventListener('click', event => {
        if (event.target.closest('a')) {
          nav.classList.remove('open');
          navToggle.setAttribute('aria-expanded', 'false');
          navToggle.setAttribute('aria-label', '打开导航');
        }
      });
      document.addEventListener('keydown', event => {
        if (event.key === 'Escape' && nav.classList.contains('open')) {
          nav.classList.remove('open');
          navToggle.setAttribute('aria-expanded', 'false');
          navToggle.setAttribute('aria-label', '打开导航');
          navToggle.focus();
        }
      });
    }

    const filters = document.querySelector('.filters-card');
    if (filters) {
      const mobileFilters = window.matchMedia('(max-width: 820px)');
      let userToggledFilters = false;
      const summary = filters.querySelector('summary');
      if (summary) summary.addEventListener('click', () => { userToggledFilters = true; });
      const syncFilterDisclosure = () => {
        if (!userToggledFilters) filters.open = !mobileFilters.matches;
      };
      syncFilterDisclosure();
      mobileFilters.addEventListener('change', syncFilterDisclosure);
    }

    document.querySelectorAll('[data-select-fields]').forEach(button => {
      button.addEventListener('click', () => {
        const scope = button.closest('[data-field-picker]') || button.closest('fieldset') || button.closest('.picker-menu') || button.closest('form');
        if (!scope) return;
        const boxes = scope.querySelectorAll('input[type="checkbox"][name="field"]');
        const mode = button.dataset.selectFields;
        boxes.forEach(box => {
          box.checked = mode === 'all' || (mode === 'default' && ['image', 'nameZh', 'scientificName', 'family', 'genus', 'uses', 'oilContent'].includes(box.value));
        });
      });
    });

    document.querySelectorAll('[data-password-toggle]').forEach(button => {
      button.addEventListener('click', () => {
        const input = button.parentElement.querySelector('input');
        if (!input) return;
        const visible = input.type === 'password';
        input.type = visible ? 'text' : 'password';
        button.setAttribute('aria-pressed', String(visible));
        button.setAttribute('aria-label', `${visible ? '隐藏' : '显示'}${input.name === 'confirmPassword' ? '确认密码' : '密码'}`);
        button.textContent = visible ? '隐藏' : '显示';
        input.focus();
      });
    });

    document.querySelectorAll('form[data-validate]').forEach(form => {
      form.addEventListener('submit', event => validateForm(form, event));
      form.querySelectorAll('input,select,textarea').forEach(input => input.addEventListener('input', () => clearFieldError(input)));
    });

    const password = document.getElementById('password');
    const confirmPassword = document.getElementById('confirmPassword');
    const passwordHint = document.getElementById('passwordHint');
    const updatePasswordHint = () => {
      if (!password || !confirmPassword || !passwordHint) return;
      const value = password.value;
      if (!value && !confirmPassword.value) {
        passwordHint.textContent = '';
        passwordHint.classList.remove('invalid', 'valid');
      } else if (value.length < 10 || !/[A-Za-z]/.test(value) || !/[0-9]/.test(value)) {
        passwordHint.textContent = '密码至少 10 位，并包含字母和数字。';
        passwordHint.classList.add('invalid');
        passwordHint.classList.remove('valid');
      } else if (confirmPassword.value && value !== confirmPassword.value) {
        passwordHint.textContent = '两次密码不一致。';
        passwordHint.classList.add('invalid');
        passwordHint.classList.remove('valid');
      } else if (confirmPassword.value && value === confirmPassword.value) {
        passwordHint.textContent = '密码符合要求。';
        passwordHint.classList.add('valid');
        passwordHint.classList.remove('invalid');
      } else {
        passwordHint.textContent = '密码符合长度要求。';
        passwordHint.classList.remove('invalid', 'valid');
      }
    };
    if (password) password.addEventListener('input', updatePasswordHint);
    if (confirmPassword) confirmPassword.addEventListener('input', updatePasswordHint);
    initUsernameCheck();

    document.querySelectorAll('[data-search-form] input, [data-search-form] select').forEach(input => {
      input.addEventListener('input', () => {
        const status = input.form.querySelector('[data-filter-status]');
        if (status) status.textContent = '条件已修改；运行检索后更新结果。';
      });
      input.addEventListener('change', () => {
        const status = input.form.querySelector('[data-filter-status]');
        if (status) status.textContent = '条件已修改；运行检索后更新结果。';
      });
    });

    document.querySelectorAll('form[data-confirm]').forEach(form => {
      form.addEventListener('submit', event => {
        if (!window.confirm(form.dataset.confirm)) event.preventDefault();
      });
    });

    document.querySelectorAll('[data-validate="upload"] input[type="file"]').forEach(input => {
      input.addEventListener('change', () => {
        const form = input.form;
        const preview = form.querySelector('[data-upload-preview]');
        const file = input.files && input.files[0];
        const oldUrl = input.dataset.previewUrl;
        if (oldUrl) URL.revokeObjectURL(oldUrl);
        input.dataset.previewUrl = '';
        if (!file) {
          if (preview) preview.hidden = true;
          clearFieldError(input);
          return;
        }
        if (file.size > 5 * 1024 * 1024 || !validImage(file)) {
          setFieldError(input, '请选择不超过 5 MB 的 JPG、PNG 或 WebP 图片。');
          announce(form, '请选择不超过 5 MB 的 JPG、PNG 或 WebP 图片。');
          input.value = '';
          if (preview) preview.hidden = true;
          return;
        }
        clearFieldError(input);
        if (preview) {
          const image = preview.querySelector('img');
          const url = URL.createObjectURL(file);
          input.dataset.previewUrl = url;
          image.src = url;
          image.alt = `待上传预览：${file.name}`;
          preview.hidden = false;
        }
      });
    });

    document.querySelectorAll('form').forEach(form => {
      form.addEventListener('submit', event => {
        if (event.defaultPrevented || form.dataset.submitting === 'true') return;
        const button = event.submitter || form.querySelector('button[type="submit"],button:not([type])');
        if (!button) return;
        if (button.name) {
          const submitValue = document.createElement('input');
          submitValue.type = 'hidden';
          submitValue.name = button.name;
          submitValue.value = button.value;
          form.appendChild(submitValue);
        }
        form.dataset.submitting = 'true';
        form.setAttribute('aria-busy', 'true');
        button.disabled = true;
        button.classList.add('is-loading');
        const label = button.dataset.loadingText || '正在提交…';
        button.dataset.originalLabel = button.textContent.trim();
        button.textContent = label;
      });
    });

    document.querySelectorAll('.composition-bar').forEach(bar => {
      const value = Number.parseFloat(bar.dataset.value);
      if (Number.isFinite(value)) {
        const clamped = Math.max(0, Math.min(100, value));
        bar.style.width = `${clamped}%`;
        bar.setAttribute('aria-label', `${clamped}%`);
      }
    });
  }

  if (document.readyState === 'loading') document.addEventListener('DOMContentLoaded', init);
  else init();
})();
