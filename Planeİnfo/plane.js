document.addEventListener("DOMContentLoaded", () => {
  document.querySelectorAll('.seat').forEach((seat) => {
    seat.addEventListener('click', () => {
      const current = seat.getAttribute('fill');

      if (current === 'white') {
        seat.setAttribute('fill', 'lightgreen');
      } else if (current === 'lightgreen') {
        seat.setAttribute('fill', 'gold');
      } else if (current === 'gold') {
        seat.setAttribute('fill', 'lightcoral');
      } else {
        seat.setAttribute('fill', 'white');
      }
    });
  });
});
