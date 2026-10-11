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
      } else if (!/^[23456789A-HJ-KM-NP-Z]{6}$/i.test(f.captchaCode.value.trim())) {
        fail('请输入图片中的 6 位验证码。');
      }
    }

    if (type === 'register') {
      const username = f.username.value.trim();
      if (!/^[A-Za-z][A-Za-z0-9_]{3,31}$/.test(username)) return fail('用户名需为 4–32 位，以字母开头，只能包含字母、数字和下划线。');
      if (f.password.value.length < 10 || f.password.value.length > 72 || !/[A-Za-z]/.test(f.password.value) || !/[0-9]/.test(f.password.value)) return fail('密码需为 10–72 位，并同时包含字母和数字。');
      if (f.password.value !== f.confirmPassword.value) return fail('两次输入的密码不一致。');
      if (!f.displayName.value.trim()) return fail('请填写显示名称。');
      if (!f.email.value || !f.email.checkValidity()) return fail('请填写有效邮箱地址。');
      if (!/^[0-9]{6}$/.test(f.emailCode.value.trim())) return fail('请输入 6 位邮箱验证码。');
      if (!/^[23456789A-HJ-KM-NP-Z]{6}$/i.test(f.captchaCode.value.trim())) return fail('请输入图片中的 6 位验证码。');
    }

    if (type === 'reset-password') {
      if (!f.email.checkValidity()) return fail('请输入有效邮箱地址。');
      if (!/^[0-9]{6}$/.test(f.emailCode.value.trim())) return fail('请输入 6 位邮箱验证码。');
      if (f.password.value.length < 10 || f.password.value.length > 72 || !/[A-Za-z]/.test(f.password.value) || !/[0-9]/.test(f.password.value)) return fail('新密码需为 10–72 位，并同时包含字母和数字。');
      if (f.password.value !== f.confirmPassword.value) return fail('两次输入的新密码不一致。');
    }

    if (type === 'description') {
      if (!f.q.value.trim()) return fail('请填写植物名称或外观描述。');
      if (f.q.value.length > 500) return fail('描述最多 500 个字符。');
      if (f.mode && f.mode.value === 'name' && f.q.value.trim().length > 160) return fail('名称搜索请控制在 160 个字符以内；也可以切换到智能搜索。');
    }

    if (type === 'compare') {
      const selected = form.querySelectorAll('input[name="id"]:checked');
      if (selected.length < 2 || selected.length > 3) return fail('请选择 2–3 种植物进行对比。');
    }

    if (type === 'trait-record') {
      if (!f.plantId.value) return fail('请选择要记录特征的植物。');
      if (!f.traitCode.value || !f.optionCode.value) return fail('请选择特征和对应的特征值。');
      if (!f.sourceId.value) return fail('请为该特征选择来源。');
    }

    if (type === 'trait-source') {
      if (!f.sourceName.value.trim()) return fail('请填写来源名称。');
      if (f.sourceUrl.value && !f.sourceUrl.checkValidity()) return fail('来源链接格式无效。');
    }

    if (type === 'fatty-acid-review') {
      const value = f.reviewedValue.value;
      if (value === '' || !Number.isFinite(Number(value)) || Number(value) < 0 || Number(value) > 100) return fail('请对照原图手工输入 0–100 范围内的组分数值。');
      if (f.reviewedUnit.value !== '%') return fail('没有明确来源支持的百分比单位时，不能确认组分。');
      if (!f.reviewSourceId.value) return fail('请选择已核验的数值/单位证据来源。');
      if (f.sourceExcerpt.value.trim().length < 8) return fail('请填写具体的来源数据依据。');
      if (f.reason.value.trim().length < 5) return fail('请说明你如何核对了行、列与小数点。');
    }

    if (type === 'fatty-acid-reject' && f.reason.value.trim().length < 5) return fail('请说明无法可靠识别的原因。');

    if (type === 'gbif-lookup' && !f.plantId.value) return fail('请选择要查询的植物。');
    if (type === 'taxon-review' && f.reason.value.trim().length < 8) return fail('请填写至少 8 个字符的分类匹配审核依据。');

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

  function initPlantSuggestions() {
    document.querySelectorAll('[data-plant-suggest]').forEach(input => {
      const root = input.closest('[data-unified-search]') || input.form;
      const list = root && root.querySelector('[role="listbox"]');
      const nativeList = input.list;
      const status = root && root.querySelector('[data-suggestion-status]');
      if (!list && !nativeList) return;
      let timer = 0;
      let controller = null;
      let sequence = 0;
      let activeIndex = -1;
      let items = [];
      let options = [];
      const close = () => {
        if (!list) return;
        list.hidden = true;
        list.replaceChildren();
        options = [];
        activeIndex = -1;
        input.setAttribute('aria-expanded', 'false');
        input.removeAttribute('aria-activedescendant');
      };
      const selectItem = item => {
        input.value = item.nameZh || item.scientificName || '';
        const mode = root && root.querySelector('[data-search-mode-value]');
        if (mode) {
          mode.value = 'name';
          root.dataset.activeMode = 'name';
          root.querySelectorAll('[data-search-mode]').forEach(button => button.setAttribute('aria-pressed', String(button.dataset.searchMode === 'name')));
          const panel = root.querySelector('[data-appearance-panel]');
          const form = root.querySelector('[data-search-mode-form]');
          const examples = root.querySelector('[data-description-examples]');
          if (panel) panel.hidden = true;
          if (form) form.hidden = false;
          if (examples) examples.hidden = true;
          input.placeholder = '输入中文名、别名或拉丁学名……';
        }
        close();
        if (status) status.textContent = '已选中名称建议，正在搜索。';
        input.form.requestSubmit();
      };
      const renderList = results => {
        if (!list) return;
        items = results;
        options = [];
        list.replaceChildren();
        results.forEach((item, index) => {
          const option = document.createElement('li');
          option.id = `plant-suggestion-${sequence}-${index}`;
          option.setAttribute('role', 'option');
          option.setAttribute('aria-selected', 'false');
          const name = document.createElement('span');
          name.className = 'suggestion-name';
          name.textContent = item.nameZh || item.scientificName || '未命名植物';
          const scientific = document.createElement('i');
          scientific.textContent = item.scientificName || '学名待核验';
          option.append(name, scientific);
          option.addEventListener('mousedown', event => event.preventDefault());
          option.addEventListener('click', () => selectItem(item));
          list.append(option);
          options.push(option);
        });
        list.hidden = results.length === 0;
        input.setAttribute('aria-expanded', String(results.length > 0));
      };
      input.addEventListener('input', () => {
        window.clearTimeout(timer);
        if (controller) controller.abort();
        sequence++;
        close();
        const query = input.value.trim();
        if (status) status.textContent = '';
        if (query.length < 2 || query.length > 100) {
          if (nativeList) nativeList.replaceChildren();
          return;
        }
        const mode = root && root.querySelector('[data-search-mode-value]');
        if (mode && mode.value === 'appearance') return;
        if (mode && mode.value === 'smart' && /(叶片|叶子|叶缘|花朵|果实|果子|像|形状|树皮|长着|长有|开.{0,4}花|秋天|秋季|冬天|生长在|松塔|松果|球果)/.test(query)) {
          if (status) status.textContent = '这看起来像外观描述；提交后会提取特征，不显示名称建议。';
          return;
        }
        timer = window.setTimeout(async () => {
          const currentSequence = sequence;
          controller = new AbortController();
          if (status) status.textContent = '正在查找名称建议…';
          try {
            const response = await fetch(`${window.APP_CONTEXT || ''}/suggest?q=${encodeURIComponent(query)}`, { signal: controller.signal, headers: { Accept: 'application/json' } });
            const payload = await response.json();
            const items = Array.isArray(payload) ? payload : payload && payload.success === true && payload.status === 'SUCCESS' && Array.isArray(payload.data) ? payload.data : null;
            if (!response.ok || !items) {
              const error = new Error('suggestion request failed');
              error.status = payload && payload.status;
              throw error;
            }
            if (currentSequence !== sequence || input.value.trim() !== query) return;
            if (list) renderList(items.slice(0, 10));
            if (nativeList) nativeList.replaceChildren(...items.slice(0, 10).map(item => {
              const option = document.createElement('option');
              option.value = item.nameZh || '';
              option.label = item.nameZh && item.nameZh === item.scientificName
                ? `中文名待核 · 学名 ${item.scientificName}`
                : item.scientificName || '学名待核验';
              return option;
            }));
            if (status && list) status.textContent = items.length ? `找到 ${Math.min(items.length, 10)} 条名称建议；可用方向键选择。` : '没有名称建议，仍可直接搜索。';
          } catch (error) {
            if (error.name !== 'AbortError' && currentSequence === sequence) {
              if (nativeList) nativeList.replaceChildren();
              close();
              if (status) status.textContent = error.status === 'RATE_LIMITED'
                ? '名称建议请求较频繁，请稍后重试；你仍可直接搜索。'
                : '名称建议暂不可用，你仍可直接搜索。';
            }
          }
        }, 220);
      });
      input.addEventListener('keydown', event => {
        if (!list) return;
        if (event.key === 'Escape' && !list.hidden) {
          event.preventDefault();
          close();
          if (status) status.textContent = '已关闭名称建议。';
        } else if (!list.hidden && options.length && event.key === 'ArrowDown') {
          event.preventDefault();
          activeIndex = (activeIndex + 1) % options.length;
          options.forEach((option, index) => option.setAttribute('aria-selected', String(index === activeIndex)));
          input.setAttribute('aria-activedescendant', options[activeIndex].id);
          options[activeIndex].scrollIntoView({ block: 'nearest' });
        } else if (!list.hidden && options.length && event.key === 'ArrowUp') {
          event.preventDefault();
          activeIndex = activeIndex < 1 ? options.length - 1 : activeIndex - 1;
          options.forEach((option, index) => option.setAttribute('aria-selected', String(index === activeIndex)));
          input.setAttribute('aria-activedescendant', options[activeIndex].id);
          options[activeIndex].scrollIntoView({ block: 'nearest' });
        } else if (!list.hidden && options.length && event.key === 'Enter' && activeIndex >= 0) {
          event.preventDefault();
          if (items[activeIndex]) selectItem(items[activeIndex]);
        }
      });
    });
  }

  function markSearchResultsStale(root) {
    const previous = document.querySelectorAll('[data-result-state], .parsed-description, .search-results, .match-results, section.empty-state');
    let changed = false;
    previous.forEach(result => {
      if (result.hidden) return;
      result.hidden = true;
      changed = true;
    });
    const notice = root && root.querySelector('[data-stale-results-notice]');
    if (changed && notice) notice.hidden = false;
  }

  function initUnifiedSearch() {
    document.querySelectorAll('[data-unified-search]').forEach(root => {
      const input = root.querySelector('[data-plant-suggest]');
      const searchForm = root.querySelector('[data-search-mode-form]');
      const modeValue = root.querySelector('[data-search-mode-value]');
      const appearancePanel = root.querySelector('[data-appearance-panel]');
      const professionalLink = root.querySelector('[data-professional-link]');
      const hint = root.querySelector('[data-search-hint]');
      const modeHint = root.querySelector('[data-mode-hint]');
      const examples = root.querySelector('[data-description-examples]');
      const setMode = mode => {
        const next = ['smart', 'name', 'appearance'].includes(mode) ? mode : 'smart';
        const previousMode = modeValue ? modeValue.value : root.dataset.activeMode;
        if (previousMode !== next) markSearchResultsStale(root);
        root.dataset.activeMode = next;
        if (modeValue) modeValue.value = next;
        root.querySelectorAll('[data-search-mode]').forEach(button => button.setAttribute('aria-pressed', String(button.dataset.searchMode === next)));
        const isAppearance = next === 'appearance';
        if (examples) examples.hidden = next === 'name';
        if (searchForm) searchForm.hidden = isAppearance;
        if (appearancePanel) appearancePanel.hidden = !isAppearance;
        if (input) input.placeholder = next === 'name' ? '输入中文名、别名或拉丁学名……' : '描述你看到的叶片、花朵、颜色或果实……';
        if (modeHint) {
          const text = next === 'name' ? '按中文名、别名或拉丁学名查找；可以不选建议直接提交。'
            : isAppearance ? '只选择你确定的外观；不确定的特征可以跳过。'
              : '描述日常所见即可；也可直接输入中文名或拉丁学名。';
          const status = hint.querySelector('[data-suggestion-status]');
          modeHint.textContent = text;
          if (status) status.textContent = '';
        }
        const list = root.querySelector('[role="listbox"]');
        if (list) {
          list.hidden = true;
          if (input) input.setAttribute('aria-expanded', 'false');
        }
        if (input) input.dispatchEvent(new Event('input', { bubbles: true }));
        if (isAppearance) {
          const first = root.querySelector('[data-quick-trait]') || root.querySelector('[data-trait-select]');
          if (first) first.focus();
        } else if (input) input.focus();
      };
      root.querySelectorAll('[data-search-mode]').forEach(button => button.addEventListener('click', () => setMode(button.dataset.searchMode)));
      if (input && professionalLink) input.addEventListener('input', () => {
        const url = new URL(professionalLink.href, window.location.origin);
        const query = input.value.trim();
        if (query) url.searchParams.set('q', query); else url.searchParams.delete('q');
        professionalLink.href = url.pathname + url.search;
        markSearchResultsStale(root);
      });
      root.querySelectorAll('[data-search-example]').forEach(example => example.addEventListener('click', () => {
        if (!input) return;
        input.value = example.dataset.searchExample || '';
        input.dispatchEvent(new Event('input', { bubbles: true }));
        input.focus();
      }));
      const clear = root.querySelector('.search-clear');
      if (clear) clear.addEventListener('click', () => window.setTimeout(() => {
        if (input) { input.value = ''; input.dispatchEvent(new Event('input', { bubbles: true })); input.focus(); }
        const status = root.querySelector('[data-suggestion-status]');
        if (status) status.textContent = '已清空搜索内容。';
      }, 0));
      const initial = root.dataset.activeMode;
      if (examples && initial === 'name') examples.hidden = true;
      if (initial === 'appearance') {
        if (searchForm) searchForm.hidden = true;
        if (appearancePanel) appearancePanel.hidden = false;
      }
    });
    document.querySelectorAll('[data-search-mode]').forEach(button => {
      if (button.closest('[data-unified-search]')) return;
      button.addEventListener('click', () => {
        const root = document.querySelector('[data-unified-search]');
        const target = root && Array.from(root.querySelectorAll('.search-mode-button')).find(item => item.dataset.searchMode === button.dataset.searchMode);
        if (target) target.click();
      });
    });
  }

  function initTraitSelectors() {
    document.querySelectorAll('[data-trait-filter-form]').forEach(form => {
      const root = form.closest('[data-unified-search]') || form.closest('.trait-explorer-panel') || document;
      const syncVisualChoices = () => root.querySelectorAll('[data-quick-trait]').forEach(button => {
        const select = Array.from(form.querySelectorAll('[data-trait-select]')).find(item => item.dataset.traitSelect === button.dataset.quickTrait);
        button.setAttribute('aria-pressed', String(!!select && select.value === button.dataset.quickOption));
      });
      const renderCurrent = () => {
        const summary = form.querySelector('[data-current-traits]');
        const list = form.querySelector('[data-current-trait-list]');
        if (!summary || !list) return;
        list.replaceChildren();
        form.querySelectorAll('[data-trait-select]').forEach(select => {
          if (!select.value) return;
          const fieldset = select.closest('[data-trait-choice]');
          const legend = fieldset && fieldset.querySelector('legend');
          const selected = select.selectedOptions[0];
          if (!fieldset || !legend || !selected) return;
          const chip = document.createElement('span');
          chip.className = 'current-trait-chip';
          const label = document.createElement('span');
          const negative = fieldset.querySelector('.trait-negation input');
          label.textContent = `${legend.textContent.trim()}：${negative && negative.checked ? '不含 ' : ''}${selected.textContent.trim()}`;
          const clear = document.createElement('button');
          clear.type = 'button';
          clear.dataset.clearTrait = select.dataset.traitSelect;
          clear.setAttribute('aria-label', `移除${legend.textContent.trim()}条件`);
          clear.textContent = '移除';
          chip.append(label, clear);
          list.append(chip);
        });
        summary.hidden = list.childElementCount === 0;
      };
      const update = select => {
        const fieldset = select.closest('[data-trait-choice]');
        if (!fieldset) return;
        const negative = fieldset.querySelector('.trait-negation input');
        if (negative) {
          negative.disabled = !select.value;
          if (!select.value) negative.checked = false;
        }
        const option = select.selectedOptions[0];
        const count = option ? Number(option.dataset.confirmedPlants || 0) : 0;
        const coverage = fieldset.querySelector('[data-trait-coverage]');
        if (coverage) coverage.textContent = select.value ? (count > 0 ? `当前选择可检索 ${count} 株` : '当前选择暂无已核验记录') : '不确定的特征可以跳过';
      };
      form.querySelectorAll('[data-trait-select]').forEach(select => {
        select.addEventListener('change', () => {
          update(select);
          syncVisualChoices();
          markSearchResultsStale(root);
          const status = form.querySelector('[data-filter-status]');
          if (status) status.textContent = '条件已修改；运行检索后更新结果。';
          renderCurrent();
        });
        update(select);
      });
      form.querySelectorAll('.trait-negation input').forEach(input => input.addEventListener('change', () => { markSearchResultsStale(root); renderCurrent(); }));
      form.querySelector('[data-reset-traits]')?.addEventListener('click', () => {
        form.querySelectorAll('[data-trait-select]').forEach(select => { select.value = ''; update(select); });
        form.querySelectorAll('.trait-negation input').forEach(input => { input.checked = false; input.disabled = true; });
        syncVisualChoices();
        renderCurrent();
        markSearchResultsStale(root);
        const status = form.querySelector('[data-filter-status]');
        if (status) status.textContent = '已清除全部特征条件。';
      });
      form.addEventListener('click', event => {
        const button = event.target.closest('[data-clear-trait]');
        if (!button) return;
        const select = Array.from(form.querySelectorAll('[data-trait-select]')).find(item => item.dataset.traitSelect === button.dataset.clearTrait);
        if (!select) return;
        select.value = '';
        select.dispatchEvent(new Event('change', { bubbles: true }));
        select.focus();
      });
      const secondary = form.querySelector('[data-secondary-traits]');
      if (secondary && Array.from(secondary.querySelectorAll('[data-trait-select]')).some(select => select.value)) secondary.open = true;
      syncVisualChoices();
      renderCurrent();
    });
    document.querySelectorAll('[data-quick-trait]').forEach(button => button.addEventListener('click', () => {
      const form = button.closest('[data-unified-search], .trait-explorer-panel')?.querySelector('[data-trait-filter-form]');
      if (!form) return;
      const select = Array.from(form.querySelectorAll('[data-trait-select]')).find(item => item.dataset.traitSelect === button.dataset.quickTrait);
      if (!select) return;
      select.value = select.value === button.dataset.quickOption ? '' : button.dataset.quickOption;
      select.dispatchEvent(new Event('change', { bubbles: true }));
      const status = form.querySelector('[data-filter-status]');
      if (status) status.textContent = '已更新外观条件；运行检索后查看结果。';
    }));
    document.querySelectorAll('[data-clear-trait]').forEach(button => button.addEventListener('click', () => {
      if (button.closest('[data-trait-filter-form]')) return;
      const select = Array.from(document.querySelectorAll('[data-trait-select]')).find(item => item.dataset.traitSelect === button.dataset.clearTrait);
      if (!select) return;
      select.value = '';
      select.dispatchEvent(new Event('change', { bubbles: true }));
      select.focus();
    }));
  }

  function showPlantImageFallback(image) {
    if (!image || image.dataset.fallbackShown === 'true') return;
    image.dataset.fallbackShown = 'true';
    const frame = image.closest('[data-image-frame]');
    const frameFallback = frame && frame.querySelector('[data-image-fallback]');
    if (frameFallback) {
      image.hidden = true;
      frameFallback.hidden = false;
      return;
    }
    const fallback = document.createElement('span');
    fallback.className = 'image-fallback-generic';
    fallback.setAttribute('role', 'img');
    fallback.setAttribute('aria-label', '暂无可用图片');
    fallback.textContent = '暂无可用图片';
    image.replaceWith(fallback);
  }

  function initPlantImageFallbacks() {
    document.querySelectorAll('.detail-photo img, .candidate-image img, .plant-card-image img, .history-plant img').forEach(image => {
      image.addEventListener('error', () => showPlantImageFallback(image), { once: true });
      if (image.complete && image.naturalWidth === 0) showPlantImageFallback(image);
    });
  }

  function init() {
    initPlantImageFallbacks();
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

    document.querySelectorAll('[data-captcha-refresh]').forEach(button => {
      button.addEventListener('click', () => {
        const row = button.closest('.captcha-row');
        const image = row && row.querySelector('.captcha-image');
        const hint = row && row.querySelector('.field-hint');
        if (!image) return;
        const purpose = new URL(image.src, window.location.href).searchParams.get('purpose');
        const url = `${window.APP_CONTEXT || ''}/captcha?purpose=${encodeURIComponent(purpose || 'login')}&refresh=1&_=${Date.now()}`;
        if (hint) hint.textContent = '验证码刷新请求已发送；若图片没有变化，请稍后再试。';
        image.onerror = () => { if (hint) hint.textContent = '刷新过于频繁，请稍等片刻后重试。'; };
        image.onload = () => { if (hint) hint.textContent = '请输入图片中的 6 个字符。'; };
        image.src = url;
      });
    });

    document.querySelectorAll('[data-email-code]').forEach(button => {
      button.addEventListener('click', async () => {
        const form = button.closest('form');
        const emailInput = document.getElementById(button.dataset.emailInput || '');
        const status = form && form.querySelector('[data-email-message]');
        if (!form || !emailInput || !status) return;
        if (button.dataset.emailPurpose !== 'verify' && !emailInput.checkValidity()) {
          emailInput.reportValidity();
          return;
        }
        const csrf = form.elements.csrfToken && form.elements.csrfToken.value;
        button.disabled = true;
        status.textContent = '正在提交验证码请求…';
        try {
          const response = await fetch(`${window.APP_CONTEXT || ''}/email-code`, {
            method: 'POST',
            headers: { 'Content-Type': 'application/x-www-form-urlencoded;charset=UTF-8', 'X-CSRF-Token': csrf || '' },
            body: new URLSearchParams({ purpose: button.dataset.emailPurpose || '', email: emailInput.value })
          });
          const result = await response.json();
          status.textContent = result.message || (response.ok ? '若邮箱可用，验证码已发送。' : '请求失败，请稍后重试。');
          status.classList.toggle('invalid', !response.ok);
          if (response.status === 202) {
            let seconds = 60;
            button.textContent = `请稍候 ${seconds} 秒`;
            const timer = window.setInterval(() => {
              seconds -= 1;
              if (seconds <= 0) { window.clearInterval(timer); button.disabled = false; button.textContent = '重新发送验证码'; }
              else button.textContent = `请稍候 ${seconds} 秒`;
            }, 1000);
          } else button.disabled = false;
        } catch (_) {
          status.textContent = '邮件服务暂时不可用，请稍后重试。';
          status.classList.add('invalid');
          button.disabled = false;
        }
      });
    });

    const traitSelect = document.querySelector('[data-trait-option-filter]');
    const traitOptionSelect = document.getElementById('trait-option');
    if (traitSelect && traitOptionSelect) {
      const filterTraitOptions = () => {
        const traitCode = traitSelect.value;
        let selectedStillVisible = false;
        [...traitOptionSelect.options].forEach(option => {
          if (!option.dataset.traitCode) return;
          const visible = !traitCode || option.dataset.traitCode === traitCode;
          option.hidden = !visible;
          if (!visible && option.selected) option.selected = false;
          if (visible && option.selected) selectedStillVisible = true;
        });
        if (!selectedStillVisible) traitOptionSelect.value = '';
      };
      traitSelect.addEventListener('change', filterTraitOptions);
      filterTraitOptions();
    }

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
    initPlantSuggestions();
    initUnifiedSearch();
    initTraitSelectors();

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
        const loadingStatus = form.querySelector('[data-loading-status], [data-filter-status]');
        if (loadingStatus) loadingStatus.textContent = label;
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
