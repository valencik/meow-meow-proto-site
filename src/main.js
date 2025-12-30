function handleBurgerClick(el) {
  const target = el.dataset.target;
  const $target = document.getElementById(target);
  el.classList.toggle('bulma-is-active');
  $target.classList.toggle('bulma-is-active');
}
