document.addEventListener('DOMContentLoaded', function() {
    // DOM Elements
    const createRoomBtn = document.getElementById('create-room-btn');
    const joinRoomBtn = document.getElementById('join-room-btn');
    const createRoomForm = document.getElementById('create-room-form');
    const joinRoomForm = document.getElementById('join-room-form');
    const roomCreatedDiv = document.getElementById('room-created');
    const startGameBtn = document.getElementById('start-game-btn');
    const joinBtn = document.getElementById('join-btn');

    // Dropdown elements
    const ageGroupSelect = document.getElementById('age-group');
    const customAgeGroupInput = document.getElementById('custom-age-group');
    const topicSelect = document.getElementById('topic');
    const customTopicInput = document.getElementById('custom-topic');

    // Event Listeners
    createRoomBtn.addEventListener('click', showCreateRoomForm);
    joinRoomBtn.addEventListener('click', showJoinRoomForm);

    // Age group dropdown logic
    ageGroupSelect.addEventListener('change', function() {
        if (this.value === '') {
            customAgeGroupInput.classList.remove('hidden');
        } else {
            customAgeGroupInput.classList.add('hidden');
        }
    });

    // Topic dropdown logic
    topicSelect.addEventListener('change', function() {
        if (this.value === '') {
            customTopicInput.classList.remove('hidden');
        } else {
            customTopicInput.classList.add('hidden');
        }
    });

    // Start game button
    startGameBtn.addEventListener('click', startGame);

    // Join room button
    joinBtn.addEventListener('click', joinRoom);

    // Functions
    function showCreateRoomForm() {
        createRoomForm.classList.remove('hidden');
        joinRoomForm.classList.add('hidden');
    }

    function showJoinRoomForm() {
        joinRoomForm.classList.remove('hidden');
        createRoomForm.classList.add('hidden');
    }

    function startGame() {
        // Validate form
        const ageGroup = ageGroupSelect.value || customAgeGroupInput.value;
        const topic = topicSelect.value || customTopicInput.value;

        if (!ageGroup || !topic) {
            alert('Please fill in both age group and topic');
            return;
        }

        // Generate room number (6 digits)
        const roomNumber = Math.floor(100000 + Math.random() * 900000);
        document.getElementById('room-number').textContent = roomNumber;

        // Show room created section
        createRoomForm.classList.add('hidden');
        roomCreatedDiv.classList.remove('hidden');

        // Store room info in localStorage for game page
        localStorage.setItem('roomNumber', roomNumber);
        localStorage.setItem('ageGroup', ageGroup);
        localStorage.setItem('topic', topic);

        // Redirect to game page after 3 seconds
        setTimeout(() => {
            window.location.href = `/game?roomCode=${roomNumber}&ageGroup=${encodeURIComponent(ageGroup)}&topic=${encodeURIComponent(topic)}`;
        }, 3000);
    }

    function joinRoom() {
        const roomCode = document.getElementById('room-code').value;

        if (roomCode.length !== 6 || isNaN(roomCode)) {
            alert('Please enter a valid 6-digit room code');
            return;
        }

        // For now, just redirect to game page
        localStorage.setItem('roomNumber', roomCode);
        window.location.href = `/game?roomCode=${roomCode}`;
    }
});