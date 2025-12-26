function handleBurgerClick(el) {
  const target = el.dataset.target;
  const $target = document.getElementById(target);
  el.classList.toggle('is-active');
  $target.classList.toggle('is-active');
}
