function debounce(fn, delay = 300) {
  let t;
  return (...args) => {
    clearTimeout(t);
    t = setTimeout(() => fn(...args), delay);
  };
}

document.addEventListener('DOMContentLoaded', () => {
  // —— Password Show/Hide Toggle —— 
  const toggle = document.querySelector('.toggle-password');
  const pwd = document.getElementById('password');
  if (toggle && pwd) {
    toggle.addEventListener('click', () => {
      if (pwd.type === 'password') {
        pwd.type = 'text';
        toggle.textContent = 'Hide';
      } else {
        pwd.type = 'password';
        toggle.textContent = 'Show';
      }
    });
  }
  const tbody = document.getElementById('book-tbody');
  const pagination = document.querySelector('.pagination');
  // Cache the original table content
  const originalRows = tbody ? tbody.innerHTML : '';
  // Cache the original display status of pagination
  const origPaginationDisplay = pagination
    ? window.getComputedStyle(pagination).display
    : 'block';

  // —— Autocomplete (/suggest) —— 
  const searchInput = document.getElementById('searchInput');
  const suggestionsBox = document.getElementById('suggestions');
  if (searchInput && suggestionsBox) {
    searchInput.addEventListener('input', debounce(() => {
      const q = searchInput.value.trim();
      if (!q) {
        suggestionsBox.innerHTML = '';
        return;
      }
      fetch(`/suggest?q=${encodeURIComponent(q)}`)
        .then(res => res.json())
        .then(data => {
          suggestionsBox.innerHTML = data.map(b =>
            `<div class="sugg-item">${b.title} (${b.author})</div>`
          ).join('');
          suggestionsBox.querySelectorAll('.sugg-item').forEach(div => {
            div.addEventListener('click', () => {
              // When clicking on the suggestion item, fill in the input box and clear the suggestion
              searchInput.value = div.textContent.replace(/\s*\(.+$/, '');
              suggestionsBox.innerHTML = '';
            });
          });
        })
        .catch(() => { suggestionsBox.innerHTML = ''; });
    }, 200));

    // Click elsewhere to close the suggestion box
    document.addEventListener('click', e => {
      if (!suggestionsBox.contains(e.target) && e.target !== searchInput) {
        suggestionsBox.innerHTML = '';
      }
    });
  }

  // —— Cross-page real-time filtering  —— 
  const filterInput = document.getElementById('filterInput');
  if (filterInput && tbody && pagination) {
    filterInput.addEventListener('input', () => {
      const term = filterInput.value.trim();

      if (!term) {
        // When clearing the filter: Restore the original table + pagination display
        tbody.innerHTML = originalRows;
        pagination.style.display = origPaginationDisplay;
        return;
      }

      // When there are filter words: Hide the page
      pagination.style.display = 'none';

      fetch(`/api/books?filter=${encodeURIComponent(term)}`)
        .then(res => res.json())
        .then(rows => {
          if (!rows.length) {
            tbody.innerHTML = `<tr><td colspan="5" class="no-data">No books found.</td></tr>`;
            return;
          }
          tbody.innerHTML = rows.map(b => {
            // Render different states based on availability
            const statusHtml = b.availability > 0
              ? `<span class="status available">Available (${b.availability})</span>`
              : `<span class="status checked-out">Checked Out</span>`;
            return `
              <tr>
                <td>${b.id}</td>
                <td>${b.title}</td>
                <td>${b.author}</td>
                <td>${statusHtml}</td>
                <td class="actions">
                  <a href=" ">Edit</a >
                  <form action="/books/${b.id}/delete" method="POST" style="display:inline"
                        onsubmit="return confirm('Delete this book?')">
                    <button type="submit">Delete</button>
                  </form>
                </td>
              </tr>
            `;
          }).join('');
        })
        .catch(() => {
          tbody.innerHTML = `<tr><td colspan="5" class="no-data">Error fetching data.</td></tr>`;
        });
    });
  }

  // —— hover highlight —— 
  document.querySelectorAll('table').forEach(table => {
    table.querySelectorAll('tr').forEach(row => {
      row.querySelectorAll('td,th').forEach((cell, i) => {
        cell.addEventListener('mouseenter', () => {
          table.querySelectorAll('tr').forEach(r => {
            const c = r.children[i];
            if (c) c.classList.add('col-hover');
          });
        });
        cell.addEventListener('mouseleave', () => {
          table.querySelectorAll('.col-hover').forEach(c => c.classList.remove('col-hover'));
        });
      });
    });
  });
  document.querySelectorAll('.btn-return').forEach(btn => {
    btn.addEventListener('click', async () => {
      const recId = btn.dataset.id;
      if (!confirm('Return this book?')) return;

      try {
        const resp = await fetch(`/api/return/${recId}`, { method: 'POST' });
        const result = await resp.json();
        if (result.success) {
          // Display the top prompt
          const msgDiv = document.getElementById('returnMessage');
          msgDiv.textContent = `Record #${recId} returned successfully.`;
          msgDiv.style.display = 'block';
          // Hide the prompt after 3 seconds
          setTimeout(() => msgDiv.style.display = 'none', 3000);
          // Remove the corresponding row
          const row = document.querySelector(`tr[data-record="${recId}"]`);
          if (row) row.remove();
        } else {
          alert('Error: ' + (result.message || 'Unknown'));
        }
      } catch (err) {
        console.error(err);
        alert('Network error, please try again.');
      }
    });
  });
});