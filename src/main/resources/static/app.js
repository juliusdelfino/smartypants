const $ = id => document.getElementById(id);

// Elements
const createBtn = $('createBtn');
const joinBtn = $('joinBtn');
const createForm = $('createForm');
const joinForm = $('joinForm');
const createRoomSubmit = $('createRoomSubmit');
const createRoomCancel = $('createRoomCancel');
const joinRoomSubmit = $('joinRoomSubmit');
const joinRoomCancel = $('joinRoomCancel');
const joinRoomId = $('joinRoomId');
const joinRoomName = $('joinRoomName');
// replaced inputs: get grids and modal elements
const ageGrid = $('ageGrid');
const topicGrid = $('topicGrid');
const topicModal = $('topicModal');
const customTopicCard = $('customTopicCard');
const customTopicInput = $('customTopicInput');
const topicModalOk = $('topicModalOk');
const topicModalCancel = $('topicModalCancel');

const roomInfo = $('roomInfo');
const roomIdDisplay = $('roomIdDisplay');
const roomAgeGroup = $('roomAgeGroup');
const roomTopic = $('roomTopic');
const playerList = $('playerList');
const top5El = $('top5');
const startBtn = $('startBtn');
const lobby = $('lobby');
const game = $('game');
const questionText = $('questionText');
const questionNumber = $('questionNumber');
const choicesEl = $('choices');
const nextBtn = $('nextBtn');
const timerEl = $('timer');
const endActions = $('endActions');
const finalScore = $('finalScore');
const finalBoard = $('finalBoard');
const backBtn = $('backBtn');

let selectedAge = 'All ages';
let selectedTopic = 'General Knowledge';
let isModerator = false; // true for room creator

let currentRoom = null;
let questions = [];
let currentIndex = 0;
let score = 0;
let timer = null;
let timeLeft = 30;
const QUESTION_TIME = 30; // seconds

// WebSocket
let sock = null;
let stompClient = null;
let playerId = null;
let playerName = null;

function connectWS(roomId) {
  return new Promise((resolve, reject) => {
    if (stompClient && stompClient.connected) return resolve();
    sock = new SockJS('/ws');
    stompClient = Stomp.over(sock);
    stompClient.connect({}, function(frame) {
      console.log('Connected: ' + frame);
      stompClient.subscribe('/topic/rooms/' + roomId, function(message) {
        const payload = JSON.parse(message.body);
        handleWsMessage(payload);
      });
      resolve();
    }, function(err) {
      console.warn('WS connect error', err);
      reject(err);
    });
  });
}

async function sendJoin(roomId, playerName) {
  try {
    await connectWS(roomId);
  } catch (e) {
    console.warn('Could not connect to WS, proceeding without real-time updates');
    return;
  }
  playerId = playerId || 'p-' + Math.random().toString(36).slice(2,9);
  playerName = playerName || ('Player-' + playerId.slice(-3));
  const payload = { playerId, name: playerName };
  stompClient.send('/app/rooms/' + roomId + '/join', {}, JSON.stringify(payload));
}

async function sendAnswer(roomId, newScore) {
  if (!stompClient) {
    try { await connectWS(roomId); } catch(e){return}
  }
  const payload = { playerId, score: newScore };
  stompClient.send('/app/rooms/' + roomId + '/answer', {}, JSON.stringify(payload));
}

function handleWsMessage(payload) {
  if (!payload || !payload.type) return;
  if (payload.type === 'players') {
    // show updated players
    const players = payload.players || [];
    renderPlayers(players);
    //renderTop5(payload.top5 || []);
  } else if (payload.type === 'top5') {
    renderTop5(payload.top5 || []);
  } else if (payload.type === 'final') {
    renderFinal(payload.scores || []);
  } else if (payload.type === 'start') {
    // moderator started the game; non-moderators should switch to game view and receive questions
    if (!isModerator) {
      questions = payload.questions || [];
      currentIndex = 0;
      score = 0;
      lobby.classList.add('hidden');
      game.classList.remove('hidden');
      endActions.classList.add('hidden');
      nextBtn.disabled = true; // only moderator can advance
      nextBtn.style.display = 'none';
      showQuestion(currentIndex);
    }
  } else if (payload.type === 'advance') {
    // moderator advanced to next question; update view for everyone
    if (!isModerator) {
      questions = payload.questions || questions;
      currentIndex = payload.index || currentIndex;
      if (currentIndex >= questions.length) {
        showEnd();
      } else {
        showQuestion(currentIndex);
      }
    }
  } else if (payload.type === 'scores_snapshot') {
    handleScoresSnapshot(payload.scores || []);
  }
}

function renderPlayers(players) {
  // display each player as a colored badge with padding
  playerList.innerHTML = '';
  players.forEach((p, i) => {
    const badge = document.createElement('div');
    badge.className = 'player-badge';
    // pick a deterministic color from name/id
    const color = pickColor(p.id || p.name || String(i));
    badge.style.background = color;
    badge.textContent = p.name + (p.score ? ` (${p.score})` : '');
    playerList.appendChild(badge);
  });
}

function pickColor(seed) {
  // simple hash to color
  let h = 0;
  for (let i=0;i<seed.length;i++) h = (h<<5)-h + seed.charCodeAt(i);
  const colors = ['#ffd54f','#ff8a65','#b39ddb','#80deea','#a5d6a7','#ffcc80','#c5e1a5','#90caf9','#ffab91','#f48fb1'];
  return colors[Math.abs(h) % colors.length];
}

function renderTop5(list) {
  top5El.innerHTML = '<strong>Top 5:</strong>' + (list.map(p => `<div>${p.name} — ${p.score}</div>`).join('') || '<div>—</div>');
}

function renderFinal(list) {
  finalBoard.innerHTML = '';
  list.forEach((p, idx) => {
    const div = document.createElement('div');
    div.className = 'player';
    if (idx === 0) div.classList.add('top1');
    else if (idx === 1) div.classList.add('top2');
    else if (idx === 2) div.classList.add('top3');
    div.textContent = `${p.name} — ${p.score}`;
    finalBoard.appendChild(div);
  });
}

// UI interactions for card selection
function setupOptionCards() {
  // age cards
  Array.from(ageGrid.children).forEach(card => {
    card.addEventListener('click', () => {
      Array.from(ageGrid.children).forEach(c => c.classList.remove('selected'));
      card.classList.add('selected');
      selectedAge = card.dataset.value || card.textContent.trim();
    });
  });
  // topic cards
  Array.from(topicGrid.children).forEach(card => {
    card.addEventListener('click', () => {
      // handle custom separately
      if (card.id === 'customTopicCard') {
        openTopicModal();
        return;
      }
      Array.from(topicGrid.children).forEach(c => c.classList.remove('selected'));
      card.classList.add('selected');
      selectedTopic = card.dataset.value || card.textContent.trim();
    });
  });
}

function openTopicModal() {
  topicModal.classList.remove('hidden');
  customTopicInput.focus();
}
function closeTopicModal() { topicModal.classList.add('hidden'); }

topicModalOk.addEventListener('click', () => {
  const val = customTopicInput.value && customTopicInput.value.trim();
  if (!val) return alert('Enter a custom topic');
  selectedTopic = val;
  // visually set selection
  Array.from(topicGrid.children).forEach(c => c.classList.remove('selected'));
  customTopicCard.classList.add('selected');
  customTopicCard.textContent = val.length > 20 ? (val.slice(0,17) + '...') : val;
  closeTopicModal();
});

topicModalCancel.addEventListener('click', () => { closeTopicModal(); });

createBtn.addEventListener('click', () => {
  createForm.classList.toggle('hidden');
  joinForm.classList.add('hidden');
  $('mainMenu').classList.add('hidden');
  setupOptionCards();
});
joinBtn.addEventListener('click', () => {
  joinForm.classList.toggle('hidden');
  createForm.classList.add('hidden');
  $('mainMenu').classList.add('hidden');
});

createRoomCancel.addEventListener('click', () => {
  createForm.classList.add('hidden');
  $('mainMenu').classList.remove('hidden');
});

joinRoomCancel.addEventListener('click', () => {
    joinForm.classList.add('hidden');
    $('mainMenu').classList.remove('hidden');
});


createRoomSubmit.addEventListener('click', async () => {
  // creator is moderator
  isModerator = true;
  const ageGroup = selectedAge || 'All ages';
  const topic = selectedTopic || 'General Knowledge';
  const res = await fetch('/api/rooms', {
    method: 'POST', headers: {'Content-Type':'application/json'},
    body: JSON.stringify({ageGroup, topic})
  });
  if (res.ok) {
    const data = await res.json();
    currentRoom = data;

    // hide all card grids and inputs and forms, only show roomInfo
    createForm.classList.add('hidden');

    // show room info
    roomIdDisplay.textContent = data.id;
    roomAgeGroup.textContent = data.ageGroup;
    roomTopic.textContent = data.topic;
    roomInfo.classList.remove('hidden');

    // moderator should connect via WS and join
    await connectWS(data.id).catch(()=>{});
    playerId = 'mod-' + Math.random().toString(36).slice(2,9);
    playerName = 'Host';
    const payload = { playerId, name: playerName };
    if (stompClient && stompClient.connected) stompClient.send('/app/rooms/' + data.id + '/join', {}, JSON.stringify(payload));

    // Only moderator sees Start and can click Next
    startBtn.style.display = 'inline-block';
    startBtn.disabled = false;
    nextBtn.style.display = 'inline-block';
    nextBtn.disabled = false;
  } else {
    alert('Failed to create room');
  }
});

joinRoomSubmit.addEventListener('click', async () => {
  const id = joinRoomId.value.trim();
  const playerName = joinRoomName.value.trim();
  if (!id) return alert('Enter room ID');
  if (!playerName) return alert('Enter your name');
  const res = await fetch('/api/rooms/' + id);
  if (res.ok) {
    const data = await res.json();
    currentRoom = data;
    // hide inputs
    joinForm.classList.add('hidden');

    roomIdDisplay.textContent = data.id;
    roomAgeGroup.textContent = data.ageGroup;
    roomTopic.textContent = data.topic;
    roomInfo.classList.remove('hidden');

    // websocket join
    await sendJoin(id, playerName);

    // non-moderators: hide start button and ensure they can't advance
    startBtn.style.display = 'none';
    nextBtn.style.display = 'none';
    isModerator = false;
  } else {
    alert('Room not found');
  }
});

startBtn.addEventListener('click', async () => {
  if (!isModerator || !currentRoom) return;
  const res = await fetch(`/api/rooms/${currentRoom.id}/start`, {method:'POST'});
  if (res.ok) {
    questions = await res.json();
    currentIndex = 0;
    score = 0;

    // hide lobby card entirely for moderator to focus on game
    lobby.classList.add('hidden');
    game.classList.remove('hidden');
    endActions.classList.add('hidden');

    // Moderator is able to advance
    nextBtn.disabled = false;
    nextBtn.style.display = 'inline-block';
    showQuestion(currentIndex);

    // Broadcast start to everyone via websocket by sending a custom message
    // server broadcasts the start event; no client notify required
  } else {
    alert('Failed to start room');
  }
});

function showQuestion(idx) {
  stopTimer();
  timeLeft = QUESTION_TIME;
  updateTimer();
  startTimer();

  const q = questions[idx];
  questionNumber.textContent = `Question ${idx+1}/${questions.length}`;
  questionText.textContent = q.question || '';
  choicesEl.innerHTML = '';
  if (!q || !q.options) {
    // Safety fallback
    const fallback = document.createElement('div');
    fallback.textContent = 'No question available.';
    choicesEl.appendChild(fallback);
    return;
  }

  q.options.forEach((c, i) => {
    const btn = document.createElement('button');
    btn.className = 'choiceBtn transition';
    btn.textContent = c;
    btn.disabled = false;
    btn.addEventListener('click', () => selectChoice(btn, i === q.correctAnswerIndex));
    choicesEl.appendChild(btn);
  });
  animateIn();
}

function revealCorrect() {
  const correctIndex = questions[currentIndex].correctAnswerIndex;
  const correct = Array.from(choicesEl.children)[correctIndex];
  if (correct) {
    correct.classList.add('correct');
    correct.classList.add('reveal');
  }
}

function selectChoice(btn, isCorrect) {
  stopTimer();
  Array.from(choicesEl.children).forEach(b => b.disabled = true);
  if (isCorrect) {
    btn.classList.add('correct');
    // award points locally for immediate UI, real scoring happens on server
    score++;
  } else {
    btn.classList.add('wrong');
    // DO NOT reveal correct here; wait until timer expiry or moderator advances
  }
  // send answer payload with correctness and timeLeft for server-side scoring
  if (playerId && stompClient && stompClient.connected) {
    const payload = { playerId, correct: !!isCorrect, timeLeft };
    stompClient.send('/app/rooms/' + currentRoom.id + '/answer', {}, JSON.stringify(payload));
  } else if (playerId) {
    // fallback: still POST to WS endpoint by connecting briefly
    sendAnswer(currentRoom.id, score);
  }

  // if last, show end (moderator will call end when done)
  if (currentIndex >= questions.length - 1) {
    setTimeout(() => { showEnd(); }, 800);
  }
  nextBtn.disabled = false;
}

// Replace previous next button behavior to call /advance so server can broadcast scores_snapshot
nextBtn.removeEventListener && nextBtn.removeEventListener('click', null);
nextBtn.addEventListener('click', async () => {
  if (!isModerator) return; // only moderator may advance
  await animateOut();
  currentIndex++;
  if (currentIndex >= questions.length) {
    // notify server of game end to broadcast final scoreboard
    fetch(`/api/rooms/${currentRoom.id}/end`, {method:'POST'}).catch(()=>{});
    showEnd();
    // broadcast final via WS will be done by server
  } else {
    nextBtn.disabled = true;
    //showQuestion(currentIndex);
    // broadcast advance message to other clients using dedicated endpoint so server can send snapshot first
    if (stompClient && stompClient.connected) {
      const payload = { type: 'advance', index: currentIndex, questions };
      stompClient.send('/app/rooms/' + currentRoom.id + '/advance', {}, JSON.stringify(payload));
    }
  }
});

function showEnd() {
  stopTimer();
  questionNumber.textContent = '';
  questionText.textContent = 'Game over — thanks for playing!';
  choicesEl.innerHTML = '';
  nextBtn.disabled = true;
  nextBtn.style.display = 'none';
  finalScore.textContent = `Score: ${score} / ${questions.length}`;
  endActions.classList.remove('hidden');
  // fetch final scores from server via websocket will arrive in renderFinal
}

backBtn.addEventListener('click', () => {
  stopTimer();
  lobby.classList.remove('hidden');
  mainMenu.classList.remove('hidden');
  game.classList.add('hidden');
  createForm.classList.add('hidden');
  endActions.classList.add('hidden');
  roomInfo.classList.add('hidden');
  nextBtn.style.display = 'none';
});

function animateIn() {
  const card = $('questionCard');
  card.classList.remove('exit');
  card.classList.add('enter');
}

function animateOut() {
  return new Promise(res => {
    const card = $('questionCard');
    card.classList.remove('enter');
    card.classList.add('exit');
    setTimeout(res, 400);
  });
}

function startTimer() {
  timerEl.textContent = timeLeft.toString();
  timer = setInterval(() => {
    timeLeft--;
    updateTimer();
    if (timeLeft <= 0) {
      stopTimer();
      Array.from(choicesEl.children).forEach(b => b.disabled = true);
      revealCorrect();
      if (currentIndex >= questions.length - 1) {
        setTimeout(() => { showEnd(); }, 800);
      }
    }
  }, 1000);
}

function stopTimer() {
    if (timer)
        clearInterval(timer);
    timer = null;
}

function updateTimer() {
  timerEl.textContent = timeLeft.toString();
}

// Handle incoming snapshot message to show ranked scoreboard temporarily
function handleScoresSnapshot(scores) {
  // show an overlay listing ranked players
  const overlay = document.createElement('div');
  overlay.className = 'scores-snapshot-overlay';
  const box = document.createElement('div');
  box.className = 'scores-box';
  const title = document.createElement('h3');
  title.textContent = 'Scores';
  box.appendChild(title);
  scores.forEach((p, idx) => {
    const row = document.createElement('div');
    row.className = 'scores-row';
    row.textContent = `${idx+1}. ${p.name} — ${p.score}`;
    box.appendChild(row);
  });
  overlay.appendChild(box);
  document.body.appendChild(overlay);
  // remove after 1.4s so server's advance payload (after 1.5s) arrives
  setTimeout(() => { document.body.removeChild(overlay); }, 1400);
}

// In handleWsMessage, add case for scores_snapshot
function handleWsMessage(payload) {
  if (!payload || !payload.type) return;
  if (payload.type === 'players') {
    // show updated players
    const players = payload.players || [];
    renderPlayers(players);
    renderTop5(payload.top5 || []);
  } else if (payload.type === 'top5') {
    renderTop5(payload.top5 || []);
  } else if (payload.type === 'final') {
    renderFinal(payload.scores || []);
  } else if (payload.type === 'start') {
    // moderator started the game; non-moderators should switch to game view and receive questions
    if (!isModerator) {
      questions = payload.questions || [];
      currentIndex = 0;
      score = 0;
      lobby.classList.add('hidden');
      game.classList.remove('hidden');
      endActions.classList.add('hidden');
      nextBtn.disabled = true; // only moderator can advance
      nextBtn.style.display = 'none';
      showQuestion(currentIndex);
    }
  } else if (payload.type === 'advance') {
    // moderator advanced to next question; update view for everyone
    if (!isModerator) {
      questions = payload.questions || questions;
      currentIndex = payload.index || currentIndex;
      if (currentIndex >= questions.length) {
        showEnd();
      } else {
        showQuestion(currentIndex);
      }
    }
  } else if (payload.type === 'scores_snapshot') {
    handleScoresSnapshot(payload.scores || []);
  }
}

// initialize selection defaults visually
(function initDefaults(){
  setupOptionCards();
  // mark defaults
  Array.from(ageGrid.children).forEach(c => { if ((c.dataset.value||c.textContent).includes('All ages')) c.classList.add('selected'); });
  Array.from(topicGrid.children).forEach(c => { if ((c.dataset.value||c.textContent).includes('General Knowledge')) c.classList.add('selected'); });
})();

// small helper to prefetch sample questions (optional)
//fetch('/data/sample_questions.json').catch(()=>{});
