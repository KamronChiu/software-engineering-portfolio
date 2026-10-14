const express = require('express');
const sqlite3 = require('sqlite3').verbose();
const session = require('express-session');
const { body, validationResult } = require('express-validator');
const path = require('path');

const app = express();
const PORT = 3000;

// —— Middleware —— 
app.use(express.urlencoded({ extended: false }));
app.use(session({
  secret: 'replace-this-with-your-own-secret',
  resave: false,
  saveUninitialized: false
}));
// —— Flash error message —— 
app.use((req, res, next) => {
  res.locals.errorMessage = req.session.errorMessage || null;
  res.locals.successMessage = req.session.successMessage || null;
  delete req.session.errorMessage;
  delete req.session.successMessage;
  next();
});

app.use(express.static(path.join(__dirname, 'public')));
app.set('view engine', 'ejs');
app.set('views', path.join(__dirname, 'views'));


// —— Auth middleware —— 
function requireLogin(req, res, next) {
  if (!req.session.user) return res.redirect('/login');
  next();
}

// —— Database setup —— 
const db = new sqlite3.Database('./library.db', err => {
  if (err) console.error('DB connection error:', err);
  else console.log('Connected to SQLite database.');
});
db.run("PRAGMA foreign_keys = ON");

db.serialize(() => {
  db.run(`
    CREATE TABLE IF NOT EXISTS users (
      id INTEGER PRIMARY KEY AUTOINCREMENT,
      name TEXT NOT NULL,
      dob  TEXT
    )
  `);
  db.run(`
    CREATE TABLE IF NOT EXISTS authors (
      id INTEGER PRIMARY KEY AUTOINCREMENT,
      name TEXT NOT NULL
    )
  `);
  db.run(`
    CREATE TABLE IF NOT EXISTS books (
      id           INTEGER PRIMARY KEY AUTOINCREMENT,
      title        TEXT NOT NULL,
      author_id    INTEGER NOT NULL,
      availability INTEGER NOT NULL DEFAULT 0,
      FOREIGN KEY(author_id) REFERENCES authors(id)
    )
  `);
  db.run(`
    CREATE TABLE IF NOT EXISTS borrow_records (
      id          INTEGER PRIMARY KEY AUTOINCREMENT,
      user_id     INTEGER NOT NULL,
      book_id     INTEGER NOT NULL,
      borrow_date TEXT    NOT NULL,
      return_date TEXT,
      FOREIGN KEY(user_id) REFERENCES users(id),
      FOREIGN KEY(book_id) REFERENCES books(id)
    )
  `);
});

// —— Real‑time search suggestions —— 
app.get('/suggest', requireLogin, (req, res) => {
  const term = (req.query.q || '').trim();
  if (!term) return res.json([]);

  db.all(`
    SELECT b.title, a.name AS author
      FROM books b
 LEFT JOIN authors a ON a.id = b.author_id
     WHERE b.title LIKE ? OR a.name LIKE ?
     LIMIT 5
  `, [`%${term}%`, `%${term}%`], (err, rows) => {
    if (err) return res.json([]);
    res.json(rows);
  });
});

// —— Login / Logout —— 
app.get('/login', (req, res) => {
  if (req.session.user) return res.redirect('/');
  res.render('login', { errors: [], old: {} });
});
app.post('/login',
  // Verify the username: 3 to 20 letters
  body('username')
    .trim()
    .notEmpty().withMessage('Username is required.')
    .isLength({ min: 3, max: 20 }).withMessage('Username must be 3–20 characters.')
    .matches(/^[A-Za-z]+$/).withMessage('Letters only.'),
  // Verification password: Any character from 6 to 20
  body('password')
    .notEmpty().withMessage('Password is required.')
    .isLength({ min: 6, max: 20 }).withMessage('Password must be 6–20 characters.'),
  (req, res) => {
    const errs = validationResult(req);
    if (!errs.isEmpty()) {
      return res.status(400).render('login', {
        errors: errs.array(),
        old: { username: req.body.username }
      });
    }
    // login successfully
    req.session.user = { name: req.body.username };
    res.redirect('/');
  }
);

app.get('/logout', (req, res) => {
  req.session.destroy(() => res.redirect('/login'));
});

// —— Home: Book Management —— 
 app.get('/', requireLogin, (req, res) => {
  const search = (req.query.search || '').trim();
  const page = parseInt(req.query.page) || 1;
  const limit = search ? 100 : (parseInt(req.query.limit) || 5);
  const offset = (page - 1) * limit;
  const sortCols = ['title', 'availability'];
  const sort = sortCols.includes(req.query.sort) ? req.query.sort : 'id';
  const authorId = parseInt(req.query.author) || null;

  let filters = [], params = [];
  if (search) {
    filters.push('(b.title LIKE ? OR a.name LIKE ?)');
    params.push(`%${search}%`, `%${search}%`);
  }
  if (authorId) {
    filters.push('b.author_id = ?');
    params.push(authorId);
  }
  const where = filters.length ? 'WHERE ' + filters.join(' AND ') : '';

  db.get(`SELECT COUNT(*) AS cnt FROM books b LEFT JOIN authors a ON a.id=b.author_id ${where}`, params, (e, r) => {
    const total = r.cnt;
    const pages = search ? 1 : Math.ceil(total / limit);

    db.all(`
      SELECT b.id, b.title, b.availability, a.name AS author
        FROM books b
   LEFT JOIN authors a ON a.id=b.author_id
      ${where}
   ORDER BY b.${sort}
      LIMIT ? OFFSET ?
    `, [...params, limit, offset], (e2, books) => {
      db.all(`SELECT * FROM authors`, (e3, authors) => {
        res.render('index', {
          books, authors, total, page, pages, limit,
          currentSort: sort, currentAuthor: authorId, search
        });
      });
    });
  });
});

// —— Create Book —— 
app.get('/books/new', requireLogin, (req, res) => {
  // Import the "authors" list
  db.all(`SELECT * FROM authors`, (_, authors) => {
    res.render('books_new', { authors, errors: [], old: {} });
  });
});

//add book validation
app.post('/books/new', requireLogin,
  body('title')
    .trim()
    .notEmpty().withMessage('Title is required.')
    .isLength({ max: 200 }).withMessage('Title too long.'),
  body('author')
    .trim()
    .notEmpty().withMessage('Author name is required.')
    .isLength({ max: 100 }).withMessage('Author name too long.'),
  body('availability')
    .notEmpty().withMessage('Availability is required.')
    .isInt({ min: 1 }).withMessage('Availability must be 1 or more.'),
  async (req, res) => {
    const errors = validationResult(req);
    const { title, author, availability } = req.body;

    // If the basic verification fails, re-render and display the error
    if (!errors.isEmpty()) {
      // display in terminal
      console.error(`[${new Date().toISOString()}] Validation errors:`, errors.array());
      return db.all(`SELECT * FROM authors`, (_, authors) =>
        res.status(400).render('books_new', {
          authors,
          errors: errors.array(),
          old: req.body
        })
      );
    }

    // Check: Whether a record with the same name and author already exists
    db.get(`
      SELECT b.id
        FROM books b
   LEFT JOIN authors a ON a.id = b.author_id
       WHERE b.title = ? AND a.name = ?
    `, [title.trim(), author.trim()], (errCheck, row) => {
      if (errCheck) {
        console.error('DB error during duplicate check:', errCheck);
        req.session.errorMessage = 'Database error, please try again.';
        return res.redirect('back');
      }

      if (row) {
        // There are already books with the same title and author
        // Print an error with a time mark
        console.error(`[${new Date().toISOString()}] Duplicate book attempt: title="${title}", author="${author}"`);
        return db.all(`SELECT * FROM authors`, (_, authors) => {
          res.status(400).render('books_new', {
            authors,
            errors: [{ msg: 'This book by this author already exists.' }],
            old: req.body
          });
        });
      }

      // No repetition. Create an author and insert a new book
      db.get(`SELECT id FROM authors WHERE name = ?`, [author.trim()], (errAuth, authRow) => {
        if (errAuth) {
          console.error('DB error selecting author:', errAuth);
          req.session.errorMessage = 'Database error, please try again.';
          return res.redirect('back');
        }

        const insertBook = authorId => {
          db.run(
            `INSERT INTO books(title, author_id, availability) VALUES(?,?,?)`,
            [title.trim(), authorId, availability],
            errIns => {
              if (errIns) {
                console.error('Insert book error:', errIns);
                req.session.errorMessage = 'Database error, please try again.';
                return res.redirect('back');
              }
              res.redirect('/');
            }
          );
        };

        if (authRow) {
          // The author already exists.
          insertBook(authRow.id);
        } else {
          // The author does not exist. Create a new author and then insert it
          db.run(`INSERT INTO authors(name) VALUES(?)`, [author.trim()], function (errNewAuth) {
            if (errNewAuth) {
              console.error('Insert author error:', errNewAuth);
              req.session.errorMessage = 'Database error, please try again.';
              return res.redirect('back');
            }
            insertBook(this.lastID);
          });
        }
      });
    });
  }
);
// —— Edit Book —— 
app.get('/books/:id/edit', requireLogin, (req, res) => {
  db.get(`SELECT * FROM books WHERE id = ?`, [req.params.id], (e, book) => {
    if (!book) {
      req.session.errorMessage = 'Book not found.';
      return res.redirect('back');
    }
    db.all(`SELECT * FROM authors`, (_, authors) => {
      res.render('books_edit', { book, authors, errors: [] });
    });
  });
});
app.post('/books/:id/edit', requireLogin,
  body('title').trim().notEmpty().withMessage('Title is required.'),
  body('author').trim().notEmpty().withMessage('Author name is required.'),
  body('availability').isInt({ min: 0 }).withMessage('Availability must be ≥ 0.'),
  (req, res) => {
    const errs = validationResult(req);
    const { title, author, availability } = req.body;
    if (!errs.isEmpty()) {
      return res.status(400).render('books_edit', {
        book: { id: req.params.id, title, author, availability },
        errors: errs.array()
      });
    }

    // 1) find or create author
    db.get(`SELECT id FROM authors WHERE name = ?`, [author.trim()], (errA, rowA) => {
      if (errA) {
        console.error('DB error finding author:', errA);
        req.session.errorMessage = 'Database error, please try again.';
        return res.redirect('back');
      }
      const doUpdate = authorId => {
        // 2) update book record
        db.run(
          `UPDATE books SET title = ?, author_id = ?, availability = ? WHERE id = ?`,
          [title.trim(), authorId, availability, req.params.id],
          errU => {
            if (errU) {
              console.error('DB error updating book:', errU);
              req.session.errorMessage = 'Update failed.';
              return res.redirect('back');
            }
            req.session.successMessage = 'Book updated successfully.';
            res.redirect('/');
          }
        );
      };

      if (rowA) {
        doUpdate(rowA.id);
      } else {
        db.run(`INSERT INTO authors(name) VALUES(?)`, [author.trim()], function(errI) {
          if (errI) {
            console.error('DB error inserting author:', errI);
            req.session.errorMessage = 'Database error, please try again.';
            return res.redirect('back');
          }
          doUpdate(this.lastID);
        });
      }
    });
  }
);
// —— Delete Book (with borrow check) —— 
app.post('/books/:id/delete', requireLogin, (req, res) => {
  const bookId = req.params.id;
  db.get(`
    SELECT COUNT(*) AS cnt
      FROM borrow_records
     WHERE book_id = ? AND return_date IS NULL
  `, [bookId], (err, row) => {
    if (err) {
      console.error('DB error checking borrow_records:', err.message);
      req.session.errorMessage = 'Database error, cannot delete.';
      return res.redirect(req.get('Referer') || '/');
    }
    if (row.cnt > 0) {
      console.error('Delete failed: book is currently borrowed.')
      req.session.errorMessage = 'Delete failed: book is currently borrowed.';
      return res.redirect(req.get('Referer') || '/');
    }
    db.run(`DELETE FROM borrow_records WHERE book_id = ?`, [bookId], errDel => {
      if (errDel) {
        console.error('Failed to clear borrow records:', errDel.message);
        req.session.errorMessage = 'Could not clear borrow records.';
        return res.redirect(req.get('Referer') || '/');
      }
      db.run(`DELETE FROM books WHERE id = ?`, [bookId], errBook => {
        if (errBook) {
          console.error('Failed to delete book:', errBook.message);
          req.session.errorMessage = 'Failed to delete book';
          console.log('Referer:', req.get('Referer')); // debug
          return res.redirect('/');
        }
        req.session.successMessage = `Book #${bookId} deleted successfully.`;
        res.redirect('/');
      });
    });
  });
});

// —— Users List —— 
app.get('/users', requireLogin, (req, res) => {
  const page = parseInt(req.query.page) || 1;
  const limit = 5;
  const offset = (page - 1) * limit;

  db.get(`SELECT COUNT(*) AS cnt FROM users`, (e, row) => {
    const totalPages = Math.ceil(row.cnt / limit);
    db.all(
      `SELECT id, name, dob
         FROM users
        ORDER BY id
        LIMIT ? OFFSET ?`,
      [limit, offset],
      (e2, users) => {
        res.render('users', {
          users,
          page,
          totalPages,
          errors: [],
          old: {}
        });
      }
    );
  });
});

// —— Add User with Validation —— 
app.post('/users/add', requireLogin,
  body('name')
    .trim()
    .notEmpty().withMessage('Name is required.')
    .matches(/^[A-Za-z\s]+$/).withMessage('Letters and spaces only.'),
  body('dob')
    .optional({ checkFalsy: true })
    .isISO8601().withMessage('Date must be YYYY-MM-DD.'),
  (req, res) => {
    const errors = validationResult(req);
    const { name, dob } = req.body;
    const page = 1, limit = 5, offset = 0;
    if (!errors.isEmpty()) {
      db.get(`SELECT COUNT(*) AS cnt FROM users`, (e, row) => {
        const totalPages = Math.ceil(row.cnt / limit);
        db.all(
          `SELECT id,name,dob FROM users ORDER BY id LIMIT ? OFFSET ?`,
          [limit, offset],
          (e2, users) => {
            return res.status(400).render('users', {
              users,
              page,
              totalPages,
              errors: errors.array(),
              old: { name, dob }
            });
          }
        );
      });
      return;
    }
    db.run(
      `INSERT INTO users (name, dob) VALUES (?, ?)`,
      [name, dob || null],
      err => {
        if (err) {
          console.error('Insert user error:', err);
          req.session.errorMessage = 'Database error, try again.';
          return res.redirect('/users');
        }
        res.redirect('/users');
      }
    );
  }
);

// —— Delete User —— 
// Expose flash messages to all views
app.use((req, res, next) => {
  // Pull messages from session, default to null
  res.locals.errorMessage   = req.session.errorMessage || null;
  res.locals.successMessage = req.session.successMessage || null;
  // Remove them from session so they only show once
  delete req.session.errorMessage;
  delete req.session.successMessage;
  next();
});

// —— Delete User Route (with borrow-record check) —— 
app.post('/users/:id/delete', requireLogin, (req, res) => {
  const userId = req.params.id;

  // 1. Check if the user has any outstanding (unreturned) borrow records
  // because when u return the book u cannot have book haven't retruned
  db.get(
    `SELECT COUNT(*) AS cnt
       FROM borrow_records
      WHERE user_id = ? AND return_date IS NULL`,
    [userId],
    (err, row) => {
      if (err) {
        // Log full error to the terminal
        console.error('DB error checking borrow_records for user:', err);
        // Show a user-friendly error on the page
        req.session.errorMessage = 'Database error, cannot delete user.';
        return res.redirect('/users');
      }

      if (row.cnt > 0) {
        // User still has books out—prevent deletion
        console.error('Delete failed: user currently has borrowed books.')
        req.session.errorMessage = 'Delete failed: user currently has borrowed books.';
        return res.redirect('/users');
      }

      // 2) No outstanding borrows—safe to delete
      db.run(
        `DELETE FROM users WHERE id = ?`,
        [userId],
        function(err2) {
          if (err2) {
            console.error('DB error deleting user:', err2);
            req.session.errorMessage = 'Database error, cannot delete user.';
          } else {
            // Report success
            req.session.successMessage = `User #${userId} deleted successfully.`;
          }
          return res.redirect('/users');
        }
      );
    }
  );
});

// —— User Detail —— 
app.get('/users/:id', requireLogin, (req, res) => {
  const uid = req.params.id;
  db.get(`SELECT * FROM users WHERE id = ?`, [uid], (e, user) => {
    if (!user) {
      req.session.errorMessage = 'User not found.';
      return res.redirect('back');
    }
    db.all(`
      SELECT br.id AS recordId, b.title, a.name AS author_name
        FROM borrow_records br
  JOIN books b ON br.book_id = b.id
  JOIN authors a ON b.author_id = a.id
       WHERE br.user_id = ? AND br.return_date IS NULL
    `, [uid], (e2, borrowedBooks) => {
      db.all(`SELECT * FROM books WHERE availability > 0`, [], (e3, availableBooks) => {
        res.render('user_detail', { user, borrowedBooks, availableBooks });
      });
    });
  });
});

// —— Borrowed Books List —— 
app.get('/borrowed', requireLogin, (req, res) => {
  db.all(`
    SELECT br.id AS recordId, u.name AS userName, b.title AS bookTitle, br.borrow_date
      FROM borrow_records br
 JOIN users u ON br.user_id = u.id
 JOIN books b ON br.book_id = b.id
     WHERE br.return_date IS NULL
  `, [], (err, records) => {
    res.render('borrowed', { records });
  });
});

// —— Borrow / Return —— 
app.post('/borrow', requireLogin, (req, res) => {
  const { user_id, book_id } = req.body;
  db.get(`SELECT availability FROM books WHERE id = ?`, [book_id], (e, b) => {
    if (b && b.availability > 0) {
      db.run(
        `INSERT INTO borrow_records (user_id, book_id, borrow_date) VALUES (?, ?, DATE('now'))`,
        [user_id, book_id],
        function (err) {
          db.run(`UPDATE books SET availability = availability - 1 WHERE id = ?`, [book_id]);
          res.redirect(`/users/${user_id}`);
        }
      );
    } else {
      req.session.errorMessage = 'Book not available.';
      res.redirect(`/users/${user_id}`);
    }
  });
});
// —— AJAX Return Endpoint —— 
app.post('/api/return/:recordId', requireLogin, (req, res) => {
  const recId = req.params.recordId;
  db.get(
    `SELECT user_id, book_id FROM borrow_records WHERE id = ?`,
    [recId],
    (err, row) => {
      if (err || !row) {
        return res.status(404).json({ success: false, message: 'Record not found' });
      }
      // Mark returned and restore the inventor
      db.run(
        `UPDATE borrow_records
           SET return_date = DATE('now')
         WHERE id = ?`,
        [recId]
      );
      db.run(
        `UPDATE books
           SET availability = availability + 1
         WHERE id = ?`,
        [row.book_id]
      );
      // Return JSON to inform the front end of success
      res.json({ success: true, recordId: recId });
    }
  );
});

// —— Real-time data filtering interface for cross-page clients —— 
app.get('/api/books', requireLogin, (req, res) => {
  const f = (req.query.filter || '').trim();
  let sql = `
    SELECT b.id, b.title, b.availability, a.name AS author
      FROM books b
 LEFT JOIN authors a ON a.id = b.author_id
  `;
  const params = [];
  if (f) {
    sql += ` WHERE b.title LIKE ? OR a.name LIKE ?`;
    params.push(`%${f}%`, `%${f}%`);
  }
  sql += ` ORDER BY b.id`;
  db.all(sql, params, (err, rows) => {
    if (err) return res.json([]);
    res.json(rows);
  });
});

// 1) 404 Handler for HTML requests
app.use((req, res, next) => {
  if (req.xhr || req.headers.accept.includes('application/json')) {
    return next();
  }
  res.status(404).render('404', { url: req.originalUrl });
});

// 2) 404 Handler for API / AJAX
app.use((req, res, next) => {
  res.status(404).json({ error: `API endpoint ${req.originalUrl} not found` });
});

// 3) Global error handler (500 errors)
app.use((err, req, res, next) => {
  if (res.headersSent) {
    return next(err);
  }
  // Print the complete error stack in the terminal
  console.error(`\n[${new Date().toISOString()}] ERROR ${req.method} ${req.originalUrl}\n`, err.stack);
  //extra
  console.error(err.stack);
  res.status(500).render('error', {
    message: 'Internal error in the server. Please try again later',
    error: process.env.NODE_ENV === 'development' ? err : {}
  });
});

app.listen(PORT, () => {
  console.log(`Server running at http://localhost:${PORT}`);
});