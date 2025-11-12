const img = new Image();
img.src = 'track.jpg';
img.onload = () => {
    const canvas = document.getElementById('canvas');
    const ctx = canvas.getContext('2d');

    // Рисуем трассу
    ctx.drawImage(img, 0, 0, canvas.width, canvas.height);

    // Получаем пиксели
    const imageData = ctx.getImageData(0, 0, canvas.width, canvas.height);
    const data = imageData.data;

    // Преобразуем пиксели в маску
    for (let i = 0; i < data.length; i += 1) {
        const r = data[i];
        const g = data[i + 1];
        const b = data[i + 2];

        // Простая логика: если цвет ближе к серому (асфальт) → белый, иначе чёрный
        const brightness = (r + g + b) / 3;
        if (brightness > 30 && brightness < 50) {
            // Асфальт (средне-серый)
            data[i] = data[i + 1] = data[i + 2] = 255; // белый
        } else {
            // Трава, бордюры и т.д.
            data[i] = data[i + 1] = data[i + 2] = 0; // чёрный
        }
    }

    // Обновляем изображение
    ctx.putImageData(imageData, 0, 0);

    // Кнопка для сохранения результата
    const btn = document.createElement('button');
    btn.textContent = '💾 Сохранить как track_mask.png';
    btn.style.marginTop = '10px';
    btn.onclick = () => {
        const link = document.createElement('a');
        link.download = 'track_mask.png';
        link.href = canvas.toDataURL('image/png');
        link.click();
    };
    document.body.appendChild(btn);
};